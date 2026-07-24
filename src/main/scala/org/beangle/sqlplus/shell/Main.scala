/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.sqlplus.shell

import org.beangle.commons.collection.Collections
import org.beangle.commons.io.{Files, IOs}
import org.beangle.commons.lang.Consoles.ColorText.{green, red}
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.lang.{JVM, Strings}
import org.beangle.commons.os.Desktops
import org.beangle.commons.xml.Document
import org.beangle.jdbc.ds.{DataSourceUtils, DatasourceConfig, Source}
import org.beangle.jdbc.engine.Engines
import org.beangle.jdbc.meta.*
import org.beangle.jdbc.query.JdbcExecutor
import org.beangle.sqlplus.lint.TempTableFinder
import org.beangle.sqlplus.lint.validator.SchemaValidator
import org.beangle.sqlplus.transport.Config.{TableConfig, ViewConfig}
import org.beangle.sqlplus.transport.{Config, Reactor, SqlAction}
import org.beangle.sqlplus.util.EncryptDataSourceUtils
import org.beangle.template.freemarker.Configurator

import java.io.{File, FileWriter, PrintWriter}
import java.sql.Connection

object Main {

  private var source: Source = _

  private var database: Database = _

  private var configurator: Configurator = _

  private val maxColumnDisplaySize = 50

  /** Max rows shown for SELECT on the console (0 = unlimited; ignored while spooling). */
  private var resultLimit = 10

  /** Max characters per column in table format. */
  private var maxColWidth = maxColumnDisplaySize

  /** Result display format for SELECT on the console (ignored while spooling). */
  private var resultFormat: ResultFormat = ResultFormat.Table

  /** Optional spool file for SELECT output (always written as CSV). */
  private var spoolFile: Option[File] = None

  enum ResultFormat:
    case Table, Vertical

  def main(args: Array[String]): Unit = {
    if args.isEmpty then
      printUsage()
      return
    if (args(0) == "transport") {
      if (args.length < 2) printUsage()
      else Reactor.main(Array(args(1)))
      return
    } else if (args(0) == "validate") {
      if (args.length < 2) printUsage()
      else SchemaValidator.main(Array(args(1)))
      return
    }

    val lastArg = args(args.length - 1)
    val configFile = if lastArg.endsWith(".xml") then lastArg else lastArg + ".xml"
    if (!new File(configFile).exists()) {
      println(s"Error:Cannot find ${configFile}")
      printUsage()
      return
    }

    configurator = new Configurator
    configurator.init()
    val xml = Document.parse(new File(configFile))
    val dbconf = EncryptDataSourceUtils.parseXml((xml \\ "source").head)
    if (null == dbconf.name) dbconf.name = Engines.forName(dbconf.driver).name.toLowerCase
    source = Source(dbconf)

    val exits = Set("exit", "quit", "q")
    val shell = new LineShell(meta = shellMeta)
    try
      var exit = false
      while !exit do
        val line = shell.readLine(s"${source.name}> ")
        if line == null then exit = true
        else
          val content = Strings.trim(line)
          if exits.contains(content) then exit = true
          else handleInput(shell, dbconf, content)
    finally
      shell.close()
  }

  /** Shared metadata for Tab completion (same cache as find/desc). */
  private val shellMeta = new ShellCompleter.Meta {
    def schemas: Seq[String] = MetadataLoader.schemas(source.dataSource)

    def relations: Seq[String] = ShellCompleter.Meta.fromDatabase(ensureDatabase())
  }

  private def ensureDatabase(): Database = {
    if database == null then database = dumpDatabase(source)
    database
  }

  private def handleInput(shell: LineShell, dbconf: DatasourceConfig, t: String): Unit = {
    t match
      case "help" => printHelp()
      case "info" => info(dbconf)
      case "dump schema" => dumpSchema(source)
      case "report schema" => reportSchema(source)
      case "validate schema" => validateSchema(source)
      case "dump data" => dumpData(source)
      case "list tmp" => listTmp(source, shell)
      case "drop tmp" => dropTmp(source, shell)
      case "list schema" => listSchema(source)
      case "set" => showSettings()
      case _ =>
        if Strings.isNotEmpty(t) then
          val cmd = t.stripLeading
          if cmd.startsWith("set ") then
            applySetting(extractParam("set ", cmd))
          else if cmd.startsWith("spool ") then
            applySpool(extractParam("spool ", cmd))
          else if cmd.equalsIgnoreCase("spool off") then
            applySpool("off")
          else if cmd.startsWith("@") then
            execScript(source, cmd.substring(1).trim)
          else if cmd.startsWith("source ") then
            execScript(source, extractParam("source ", cmd))
          else if cmd.startsWith("use ") then
            useSchema(source, extractParam("use ", cmd))
          else if cmd.startsWith("find ") then
            find(source, extractParam("find ", cmd))
          else if cmd.startsWith("desc ") then
            desc(source, extractParam("desc ", cmd))
          else if SqlStatementParser.isSqlStart(SqlStatementParser.firstSignificantLine(cmd)) then
            val (sql, vertical) = SqlStatementParser.stripTerminator(cmd)
            val format = if vertical then ResultFormat.Vertical else resultFormat
            execSql(source, sql, format)
          else
            fail(s"unknown: $t, use 'help' to get help")
  }

  private def showSettings(): Unit = {
    info(s"limit=${if resultLimit <= 0 then "unlimited" else resultLimit}")
    info(s"width=$maxColWidth")
    info(s"format=${resultFormat.toString.toLowerCase}")
    spoolFile match
      case Some(f) => info(s"spool=${f.getAbsolutePath} (csv)")
      case None => info("spool=off")
  }

  private def applySetting(expr: String): Unit = {
    val parts = Strings.split(expr.trim)
    if parts.length < 2 then
      fail("usage: set limit <n> | set width <n> | set format table|vertical")
      return
    parts(0).toLowerCase match
      case "limit" =>
        parts(1).toIntOption match
          case Some(n) if n >= 0 =>
            resultLimit = n
            success(s"limit=${if n == 0 then "unlimited" else n}")
          case _ => fail("usage: set limit <n>  (0 = unlimited)")
      case "width" =>
        parts(1).toIntOption match
          case Some(n) if n >= 3 =>
            maxColWidth = n
            success(s"width=$n")
          case _ => fail("usage: set width <n>  (min 3)")
      case "format" =>
        parts(1).toLowerCase match
          case "table" =>
            resultFormat = ResultFormat.Table
            success("format=table")
          case "vertical" | "g" =>
            resultFormat = ResultFormat.Vertical
            success("format=vertical")
          case other => fail(s"unknown format: $other (table|vertical)")
      case other => fail(s"unknown setting: $other")
  }

  private def applySpool(path: String): Unit = {
    val p = path.trim
    if p.equalsIgnoreCase("off") then
      spoolFile = None
      success("spool off")
    else
      val file = resolvePath(p)
      file.getParentFile match
        case parent if parent != null && !parent.exists() => parent.mkdirs()
        case _ =>
      spoolFile = Some(file)
      // File output is always CSV; does not change console `format`
      success(s"spool ${file.getAbsolutePath} (csv)")
  }

  private def resolvePath(path: String): File = {
    val cleaned = path.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
    if cleaned.startsWith("~") || cleaned.startsWith("/") || cleaned.contains(File.separator) then
      Files.forName(cleaned)
    else
      new File(cleaned).getAbsoluteFile
  }

  private def execScript(src: Source, path: String): Unit = {
    if Strings.isBlank(path) then
      fail("usage: @file.sql | source file.sql")
      return
    val file = resolvePath(path)
    if !file.exists() then
      fail(s"Cannot find ${file.getAbsolutePath}")
      return
    val sqls = SqlAction.readSqls(file).filter { s =>
      val t = s.trim
      Strings.isNotBlank(t) && !t.startsWith("--")
    }
    if sqls.isEmpty then
      info(s"no statements in ${file.getAbsolutePath}")
      return
    info(s"executing ${sqls.size} statement(s) from ${file.getAbsolutePath}")
    sqls.zipWithIndex.foreach { (sql, idx) =>
      info(s"-- [${idx + 1}/${sqls.size}]")
      execSql(src, sql, resultFormat)
    }
  }

  private def extractParam(cmdPrefix: String, t: String): String = {
    val cmd = if t.endsWith(";") then t.substring(0, t.length - 1).trim else t.trim
    cmd.substring(cmdPrefix.length).trim
  }

  def useSchema(src: Source, str: String): Unit = {
    val schemaNames = MetadataLoader.schemas(src.dataSource)
    schemaNames.find(x => x.toLowerCase == str.toLowerCase) match {
      case Some(d) =>
        this.source = src.copy(schema = Some(src.engine.toIdentifier(d)))
        this.database = null
        success(s"switch to schema ${str}")
      case None => fail(s"Cannot find schema ${str}")
    }
  }

  def execSql(src: Source, sql: String, format: ResultFormat = resultFormat): Unit = {
    val jdbcExecutor = new JdbcExecutor(src.dataSource)
    val sw = Stopwatch.start()
    try {
      if (sql.trim.toLowerCase.startsWith("select")) {
        val rs = jdbcExecutor.iterate(sql)
        val columnNames = rs.columnNames
        try
          if spoolFile.isDefined then
            // Spool: full result as CSV (ignore console `set limit` / format); stream rows
            val spoolWriter = openSpoolWriter()
            try
              var count = 0
              emit(ResultFormatter.csvHeader(columnNames), spoolWriter)
              while rs.hasNext do
                emit(ResultFormatter.csvRow(columnNames, rs.next()), spoolWriter)
                count += 1
              val rowLabel = if count == 1 then "(1 row)" else s"($count rows)"
              info(s"$rowLabel ($sw)")
            finally
              closeSpoolWriter(spoolWriter)
          else
            val max = if resultLimit <= 0 then Int.MaxValue else resultLimit
            val rows = Collections.newBuffer[Array[_]]
            var truncated = false
            while rs.hasNext && rows.size < max do
              rows += rs.next()
            if rs.hasNext then truncated = true
            if rows.isEmpty then
              info(s"(0 rows) ($sw)")
            else
              val lines = format match
                case ResultFormat.Table => ResultFormatter.table(columnNames, rows.toSeq, maxColWidth)
                case ResultFormat.Vertical => ResultFormatter.vertical(columnNames, rows.toSeq)
              lines.foreach(emit(_, None))
              if truncated then info("...")
              val rowLabel = if rows.size == 1 then "(1 row)" else s"(${rows.size} rows)"
              info(s"$rowLabel ($sw)")
        finally
          rs.close()
      } else {
        val affected = jdbcExecutor.update(sql.trim)
        val lower = sql.trim.toLowerCase
        if lower.startsWith("update") || lower.startsWith("insert") || lower.startsWith("delete") then
          info(s"Query OK, $affected row(s) affected ($sw)")
        else if lower.startsWith("alter ") || lower.startsWith("drop ") || lower.startsWith("create ") then
          database = null
          info(s"Query OK ($sw)")
        else
          info(s"Query OK ($sw)")
      }
    } catch
      case e: Exception => fail(s"${e.getMessage} ($sw)")
  }

  private def openSpoolWriter(): Option[PrintWriter] = {
    spoolFile.map { f =>
      new PrintWriter(new FileWriter(f, true), true)
    }
  }

  private def closeSpoolWriter(writer: Option[PrintWriter]): Unit = {
    writer.foreach(_.close())
  }

  private def emit(msg: String, spool: Option[PrintWriter]): Unit = {
    // When spooling, keep console quiet for result data (large exports).
    if spool.isEmpty then info(msg)
    else spool.foreach(_.println(msg))
  }

  def desc(src: Source, name: String): Unit = {
    ensureDatabase()
    val tables = database.findTables(name)
    tables.foreach { table =>
      val model = Map("table" -> table, "columns" -> orderedTableColumns(table))
      try {
        val desc = configurator.render("table.ftl", model)
        info(desc)
      } catch
        case e: Exception => e.printStackTrace()
    }

    val views = database.findViews(name)
    views.foreach { view =>
      val model = Map("view" -> view, "columns" -> view.columns.toSeq.sortBy(_.name.value.toLowerCase))
      try {
        val desc = configurator.render("view.ftl", model)
        info(desc)
      } catch
        case e: Exception => e.printStackTrace()
    }
  }

  /** Primary-key columns first (PK order), then other columns alphabetically. */
  private def orderedTableColumns(table: Table): Seq[Column] = {
    val pkIds = table.primaryKey.toSeq.flatMap(_.columns.toSeq)
    val pkNames = pkIds.map(_.value.toLowerCase).toSet
    val pkCols = pkIds.flatMap { id =>
      table.columns.find(_.name.value.equalsIgnoreCase(id.value))
    }
    val others = table.columns
      .filterNot(c => pkNames.contains(c.name.value.toLowerCase))
      .toSeq
      .sortBy(_.name.value.toLowerCase)
    pkCols ++ others
  }

  def find(src: Source, name: String): Unit = {
    ensureDatabase()
    val pattern = name.trim()
    var tables: Seq[Table] = Seq.empty
    var views: Seq[View] = Seq.empty

    if (pattern.startsWith("table ")) {
      tables = database.findTables(name)
    } else if (pattern.startsWith("view ")) {
      views = database.findViews(name)
    } else {
      tables = database.findTables(name)
      views = database.findViews(name)
    }

    if tables.nonEmpty || views.nonEmpty then
      if tables.nonEmpty then
        info(s"found ${tables.size} tables")
        info(tables.map(_.qualifiedName).mkString("\n"))
      if views.nonEmpty then
        info(s"found ${views.size} views")
        info(views.map(_.qualifiedName).mkString("\n"))
  }

  def info(dbconf: DatasourceConfig): Unit = {
    val res = DataSourceUtils.test(dbconf)
    if (res._1) {
      println(green("Connect successfully."))
      println(res._2)
    } else {
      println(red("Cannot connect to source:"))
      println(res._2)
    }
  }

  def reportSchema(src: Source): Unit = {
    val dbFile = Files.forName(s"~+/${src.name}.xml")
    if (!dbFile.exists()) {
      dumpSchema(src)
    }
    if (!dbFile.exists()) {
      fail("Cannot find database file: " + dbFile.getAbsolutePath)
      return
    }
    var reportxml = Files.forName(s"~+/${src.name}_report.xml")
    if (!reportxml.exists()) {
      val database = Serializer.fromXml(Files.readString(dbFile))
      val model = Map("database_file" -> dbFile.getAbsolutePath, "database" -> database)
      reportxml = Files.forName(s"~+/${src.name}_report_default.xml")
      Files.writeString(reportxml, configurator.render("report.xml.ftl", model))
    }
    org.beangle.sqlplus.report.Reporter.main(Array(reportxml.getAbsolutePath))
    val rs = Files.forName(s"~+/index.html")
    if (rs.exists()) Desktops.openBrowser(rs.getAbsolutePath)
  }

  def validateSchema(src: Source): Unit = {
    val basisFile = Files.forName(s"~+/basis.xml")
    if (!basisFile.exists()) {
      fail(s"Cannot find ${basisFile.getAbsolutePath}")
      return
    }
    val basis = Serializer.fromXml(Files.readString(basisFile))
    val engine = src.engine
    var conn: Connection = null
    try {
      conn = src.dataSource.getConnection
      val database = new Database(engine)
      val metaloader = MetadataLoader(conn, engine)
      basis.schemas foreach { s =>
        val schema = database.getOrCreateSchema(s._1.value)
        metaloader.loadTables(schema, true)
      }
      val diff = Diff.diff(database, basis)
      val sqls = Diff.sql(diff)
      if (sqls.isEmpty) println(green("OK:") + "database and xml are coincident.")
      else
        println(red("WARN:") + "database and xml are NOT coincident, and Referential migration sql are listed in diff.sql.")
        Files.writeString(Files.forName("~+/diff.sql"), sqls.mkString(";\n"))
    } finally {
      IOs.close(conn)
    }
  }

  def dumpSchema(src: Source): Unit = {
    database = dumpDatabase(src)
    val file = Files.forName(s"~+/${src.name}.xml")
    Files.writeString(file, Serializer.toXml(database))
    info(s"Dump schema into ${file.getAbsolutePath}.")
    if !JVM.isHeadless then Desktops.openBrowser(file.getAbsolutePath)
  }

  def dumpData(src: Source): Unit = {
    val srcEngine = src.engine

    val h2dump = Files.forName("~+/h2")
    val tarDbconf = new DatasourceConfig("h2")
    tarDbconf.name = "h2"
    tarDbconf.user = "sa"
    tarDbconf.props.put("url", s"jdbc:h2:file:${h2dump.getAbsolutePath}")
    tarDbconf.props.put("maximumPoolSize", "10")
    val target = Source(tarDbconf)

    val schemaNames = if src.schema.isEmpty then MetadataLoader.schemas(src.dataSource) else src.schema.map(_.value).toSeq
    val tasks = schemaNames.map { schema =>
      val from = source.parse(schema)
      val to = target.parse(schema)
      val cfg = new Config.Task(source, target).path(from, to)
      cfg.table = TableConfig.all
      cfg.view = ViewConfig.none
      cfg
    }

    info(s"start dumping into ${h2dump.getAbsolutePath}")
    new Reactor(Config(source, target, tasks)).start()
  }

  def dropTmp(src: Source, shell: LineShell): Unit = {
    val tmpPattern = shell.prompt("please input the tmp pattern:", "*log,*temp,temp*,*bak,bak*,*back,*old,old*,*tmp,tmp*,*{[0-9]+}")
    val engine = src.engine
    ensureDatabase()
    val tmpTables = TempTableFinder.find(database, tmpPattern)
    if (tmpTables.nonEmpty) {
      info(s"found ${tmpTables.size} tmp tables:")
      info(tmpTables.mkString("\n"))
      if shell.confirm("drop them?[Y/n]") then
        val executor = new JdbcExecutor(src.dataSource)
        tmpTables.foreach { table =>
          val sql = engine.dropTable(table)
          info(sql)
          executor.update(sql)
        }
      database = null
    } else {
      info(s"found 0 tmp tables.")
    }
  }

  def listSchema(src: Source): Unit = {
    val srcEngine = src.engine
    val schemaNames = MetadataLoader.schemas(src.dataSource)
    info(schemaNames.mkString("\n"))
  }

  def listTmp(src: Source, shell: LineShell): Unit = {
    val tmpPattern = shell.prompt("please input the tmp pattern:", "*log,*temp,temp*,*bak,bak*,*back,*old,old*,*tmp,tmp*,*{[0-9]+}")
    ensureDatabase()
    val tmpTables = TempTableFinder.find(database, tmpPattern)
    if (tmpTables.nonEmpty) {
      info(s"found ${tmpTables.size} tmp tables:")
      info(tmpTables.mkString("\n"))
    } else {
      info(s"found ${tmpTables.size} tmp tables.")
    }
  }

  def printUsage(): Unit = {
    val usage =
      """Usage:
        |  sqlplus <database.xml>              start interactive SQL shell
        |  sqlplus transport <conversion.xml>  transport data between databases
        |  sqlplus validate <basis.xml>        validate schema against basis.xml
        |
        |Examples:
        |  sqlplus postgres.xml
        |  sqlplus transport oracle2pg.xml
        |  sqlplus validate basis.xml""".stripMargin
    println(usage)
  }

  def printHelp(): Unit = {
    val helpString =
      """  info              display database info
        |  dump schema       extract database schema definition into database.xml
        |  report schema     create a html report of database
        |  validate schema   validate schema against a basis.xml
        |  dump data         dump data in h2 database
        |  list tmp          list temporary tables
        |  drop tmp          drop the temporary tables
        |  list schema       list all schema names
        |  find pattern      find the tables which match the pattern
        |  desc table        describe the table
        |  set               show current settings
        |  set limit n       max rows for console select (0=unlimited, default 10; ignored while spooling)
        |  set width n       max column width in table format (default 50)
        |  set format ...    table | vertical  (console; ignored while spooling)
        |  spool file        write full select results as CSV (no set limit; console shows summary)
        |  spool off         stop spooling (format/limit unchanged)
        |  @file.sql         execute SQL script (also: source file.sql)
        |  select ...;       run query (append \G for vertical once)
        |  update ...        update table set ... where ...
        |  delete ...        delete from table where ...
        |  alter table ...   alter table ...
        |  help              print this help content
        |
        |  Tip: Tab completes commands, SQL keywords, and table/schema names
        |       (column names are not completed)""".stripMargin
    info(helpString)
  }

  private def info(msg: String): Unit = {
    println(msg)
  }

  private def success(msg: String): Unit = {
    println(green(msg))
  }

  private def fail(msg: String): Unit = {
    println(red(msg))
  }

  private def dumpDatabase(src: Source): Database = {
    val engine = src.engine
    var conn: Connection = null
    try {
      conn = src.dataSource.getConnection
      MetadataLoader.dump(conn.getMetaData, engine, src.catalog, src.schema)
    } finally {
      IOs.close(conn)
    }
  }
}
