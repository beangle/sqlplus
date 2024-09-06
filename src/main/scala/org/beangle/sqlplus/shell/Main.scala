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
import org.beangle.commons.lang.{Consoles, JVM, Strings}
import org.beangle.commons.os.Desktops
import org.beangle.jdbc.ds.{DataSourceUtils, DatasourceConfig, Source}
import org.beangle.jdbc.engine.Engines
import org.beangle.jdbc.meta.*
import org.beangle.jdbc.query.JdbcExecutor
import org.beangle.sqlplus.lint.TempTableFinder
import org.beangle.sqlplus.lint.validator.SchemaValidator
import org.beangle.sqlplus.transport.Config.{TableConfig, ViewConfig}
import org.beangle.sqlplus.transport.{Config, Reactor}
import org.beangle.template.freemarker.Configurer

import java.io.File
import java.sql.Connection

object Main {

  private var source: Source = _

  private var database: Database = _

  private var configurer: Configurer = _

  private var command = Collections.newBuffer[String]

  private val maxColumnDisplaySize = 20

  def main(args: Array[String]): Unit = {
    if args.isEmpty then return
    if (args(0) == "transport") {
      if (args.length < 2) {
        println("Usage: Main transport /path/to/your/conversion.xml")
      } else Reactor.main(Array(args(1)))
      return
    } else if (args(0) == "validate") {
      if (args.length < 2) {
        println("Usage: Main validate /path/to/your/basis.xml")
      } else SchemaValidator.main(Array(args(1)))
      return
    }

    configurer = new Configurer
    configurer.init()
    val configFile = new File(args(args.length - 1))
    val xml = scala.xml.XML.loadFile(configFile)
    val dbconf = DataSourceUtils.parseXml((xml \\ "source").head)
    if (null == dbconf.name) dbconf.name = Engines.forName(dbconf.driver).name.toLowerCase
    source = Source(dbconf)

    Consoles.shell(s"${source.name}> ", Set("exit", "quit", "q"), {
      case "help" => printHelp()
      case "info" => info(dbconf)
      case "dump schema" => dumpSchema(source)
      case "report schema" => reportSchema(source)
      case "validate schema" => validateSchema(source)
      case "dump data" => dumpData(source)
      case "list tmp" => listTmp(source)
      case "drop tmp" => dropTmp(source)
      case "list schema" => listSchema(source)
      case t =>
        if (Strings.isNotEmpty(t)) {
          val cmd = t.stripLeading
          if (cmd.startsWith("use ")) {
            useSchema(source, extractParam("use ", cmd))
          } else if (cmd.startsWith("find ")) {
            find(source, extractParam("find ", cmd))
          } else if (cmd.startsWith("desc ")) {
            desc(source, extractParam("desc ", cmd))
          } else if (command.nonEmpty || cmd.startsWith("select ") || cmd.startsWith("insert ") ||
            t.startsWith("alter ") || t.startsWith("update ") || t.startsWith("delete ") ||
            t.startsWith("create ") || t.startsWith("drop ") || t.startsWith("grant ")) {
            if cmd.endsWith(";") then
              command += cmd.substring(0, cmd.length - 1)
              val sql = command.mkString(" ")
              command.clear()
              execSql(source, sql)
            else
              command += cmd
          } else
            fail(s"unknown: $t, use 'help' to get help")
        }
    })
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

  def execSql(src: Source, sql: String): Unit = {
    val jdbcExecutor = new JdbcExecutor(src.dataSource)
    try {
      if (sql.trim.toLowerCase.startsWith("select")) {
        val rs = jdbcExecutor.iterate(sql)
        val columnNames = rs.columnNames
        val displaySizes = rs.columnDisplaySizes
        for (i <- columnNames.indices) {
          if columnNames(i).length > displaySizes(i) then displaySizes(i) = columnNames(i).length
          if (displaySizes(i) > maxColumnDisplaySize) displaySizes(i) = maxColumnDisplaySize
        }
        displayResultTitle(columnNames, displaySizes)
        if (rs.hasNext) {
          val max = 10
          var i = 0
          while (rs.hasNext && i < max) {
            displayRow(rs.next(), displaySizes)
            i += 1
          }
          if (rs.hasNext) info("....")
        } else {
          info("No data")
        }
        rs.close()
      } else {
        val rows = jdbcExecutor.update(sql.trim)
        if sql.startsWith("update") || sql.startsWith("insert") || sql.startsWith("delete") then info(s"affect rows:${rows}")
        else if sql.startsWith("alter ") || sql.startsWith("drop ") || sql.startsWith("create ") then
          database = null
          info(s"done.")
      }
    } catch
      case e: Exception => fail(e.getMessage)
  }

  def displayResultTitle(columnNames: Array[String], displaySizes: Array[Int]): Unit = {
    displayRow(columnNames, displaySizes)
    val sb = new StringBuilder
    for (i <- displaySizes.indices) {
      sb.append("-" * Math.min(displaySizes(i), maxColumnDisplaySize))
      if (i < displaySizes.length - 1) sb.append("+")
    }
    info(sb.toString())
  }

  def displayRow(value: Array[_], displaySizes: Array[Int], sep: String = "|"): Unit = {
    val sb = new StringBuilder
    for (i <- displaySizes.indices) {
      var str = Strings.rightPad(String.valueOf(value(i)), displaySizes(i), ' ')
      str = Strings.abbreviate(str, maxColumnDisplaySize)
      sb.append(str)
      if (i < displaySizes.length - 1) sb.append(sep)
    }
    info(sb.toString)
  }

  def desc(src: Source, name: String): Unit = {
    if database == null then database = dumpDatabase(src)
    val tables = database.findTables(name)
    tables.foreach { table =>
      val model = Map("table" -> table)
      try {
        val desc = configurer.render("table.ftl", model)
        info(desc)
      } catch
        case e: Exception => e.printStackTrace()
    }

    val views = database.findViews(name)
    views.foreach { view =>
      val model = Map("view" -> view)
      try {
        val desc = configurer.render("view.ftl", model)
        info(desc)
      } catch
        case e: Exception => e.printStackTrace()
    }
  }

  def find(src: Source, name: String): Unit = {
    if database == null then database = dumpDatabase(src)
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
      Files.writeString(reportxml, configurer.render("report.xml.ftl", model))
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
      val metaloader = MetadataLoader(conn.getMetaData, engine)
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

  def dropTmp(src: Source): Unit = {
    val tmpPattern = Consoles.prompt("please input the tmp pattern:", "*log,*temp,temp*,*bak,bak*,*back,*old,old*,*tmp,tmp*,*{[0-9]+}")
    val engine = src.engine
    if database == null then database = dumpDatabase(src)
    val tmpTables = TempTableFinder.find(database, tmpPattern)
    if (tmpTables.nonEmpty) {
      info(s"found ${tmpTables.size} tmp tables:")
      info(tmpTables.mkString("\n"))
      if (Consoles.confirm("drop them?[Y/n]")) {
        val executor = new JdbcExecutor(src.dataSource)
        tmpTables.foreach { table =>
          val sql = engine.dropTable(table)
          info(sql)
          executor.update(sql)
        }
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

  def listTmp(src: Source): Unit = {
    val tmpPattern = Consoles.prompt("please input the tmp pattern:", "*log,*temp,temp*,*bak,bak*,*back,*old,old*,*tmp,tmp*,*{[0-9]+}")
    if database == null then database = dumpDatabase(src)
    val tmpTables = TempTableFinder.find(database, tmpPattern)
    if (tmpTables.nonEmpty) {
      info(s"found ${tmpTables.size} tmp tables:")
      info(tmpTables.mkString("\n"))
    } else {
      info(s"found ${tmpTables.size} tmp tables.")
    }
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
        |  select count(*)   select count(*) from table where ...
        |  update ...        update table set ... where ...
        |  delete ...        delete from table where ...
        |  alter table ...   alter table ...
        |  help              print this help content""".stripMargin
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
