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

package org.beangle.db.shell

import org.beangle.commons.io.{Files, IOs}
import org.beangle.commons.lang.Consoles.ColorText.{green, red}
import org.beangle.commons.lang.{Consoles, JVM}
import org.beangle.commons.os.Desktops
import org.beangle.data.jdbc.ds.{DataSourceUtils, DatasourceConfig, Source}
import org.beangle.data.jdbc.engine.Engines
import org.beangle.data.jdbc.meta.*
import org.beangle.data.jdbc.query.JdbcExecutor
import org.beangle.db.lint.TempTableFinder
import org.beangle.db.lint.validator.SchemaValidator
import org.beangle.db.transport.Config.{TableConfig, ViewConfig}
import org.beangle.db.transport.{Config, Reactor}
import org.beangle.template.freemarker.Configurer

import java.io.File
import java.sql.Connection

object Main {

  var source: Source = _

  var database: Database = _

  var configurer: Configurer = _

  def main(args: Array[String]): Unit = {
    if args.isEmpty then return
    if (args(0) == "transport") {
      if (args.length < 2) {
        println("Usage: Main transport /path/to/your/conversion.xml");
      } else Reactor.main(Array(args(1)))
      return
    } else if (args(0) == "validate") {
      if (args.length < 2) {
        println("Usage: Main validate /path/to/your/basis.xml");
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
        val cmd = if t.endsWith(";") then t.substring(0, t.length - 1).trim else t.trim
        if (cmd.startsWith("find ")) {
          findTable(source, cmd.substring("find ".length).trim())
        } else if (cmd.startsWith("desc ")) {
          descTable(source, cmd.substring("desc ".length).trim())
        } else if (cmd.startsWith("select count(*)") || t.startsWith("alter table") || t.startsWith("update ") || t.startsWith("delete ")) {
          execSql(source, cmd)
        } else fail(s"unknown: $t, use 'help' to get help")
    })
  }

  def execSql(src: Source, sql: String): Unit = {
    val jdbcExecutor = new JdbcExecutor(src.dataSource)
    try {
      if (sql.trim.toLowerCase.startsWith("select count(*)")) {
        val rs = jdbcExecutor.query(sql)
        success("data count:" + rs.map(x => x(0)).mkString(","))
      } else {
        jdbcExecutor.update(sql.trim)
        success(s"executed:${sql}")
        if (sql.trim.startsWith("alter table")) {
          database = null
        }
      }
    } catch
      case e: Exception => fail(e.getMessage)
  }

  def descTable(src: Source, name: String): Unit = {
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
  }

  def findTable(src: Source, name: String): Unit = {
    if database == null then database = dumpDatabase(src)
    val tables = database.findTables(name)
    info(tables.map(_.qualifiedName).mkString("\n"))
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
    org.beangle.db.report.Reporter.main(Array(reportxml.getAbsolutePath))
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
    success(s"Dump schema into ${file.getAbsolutePath}.")
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
