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

package org.beangle.sqlplus.report.model

import org.beangle.commons.bean.Initializing
import org.beangle.commons.collection.Collections
import org.beangle.commons.io.Files
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.jdbc.ds.{DataSourceFactory, DataSourceUtils}
import org.beangle.jdbc.engine.Engines
import org.beangle.jdbc.meta.*
import org.beangle.sqlplus.report.model.Schema as ReportSchema

import java.io.{File, FileInputStream}

object Report {

  def apply(reportXml: File): Report = {
    val xml = scala.xml.XML.load(new FileInputStream(reportXml))
    val dir = reportXml.getParent
    var database: Database = null
    if ((xml \ "db").nonEmpty) {
      val dbElem = (xml \ "db").head
      val dbconf = DataSourceUtils.parseXml(dbElem)
      database = new Database(Engines.forName(dbconf.driver))
      val ds = DataSourceFactory.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props)
      val schema = new Schema(database, database.engine.toIdentifier((dbElem \ "@schema").text))

      val conn = ds.getConnection()
      val loader = MetadataLoader(conn, Engines.forDataSource(ds))
      loader.loadTables(schema, extras = true)
      loader.loadSequences(schema)
      DataSourceUtils.close(ds)
    } else {
      val databaseXml = (xml \ "database" \ "@xml").text
      database = Serializer.fromXml(Files.readString(Files.forName(dir, databaseXml)))
    }
    val report = new Report(database)
    report.title = (xml \ "@title").text
    report.contextPath = (xml \ "@contextPath").text
    (xml \ "@reserveImageUmlScript") foreach { n =>
      report.reserveImageUmlScript = n.text.toBoolean
    }
    report.system.name = (xml \ "system" \ "@name").text
    report.system.version = (xml \ "system" \ "@version").text
    (xml \ "system" \ "props" \ "prop").foreach { ele => report.system.properties.put((ele \ "@name").text, (ele \ "@value").text) }

    (xml \ "schemas" \ "schema").foreach { ele =>
      val schema = new ReportSchema((ele \ "@name").text, (ele \ "@title").text, report)
      report.addSchema(schema)
      (ele \ "module") foreach { ele =>
        val n = (ele \ "@name").text
        val name = Some(n).filter(Strings.isNotBlank)
        val module = new Module(schema, name, (ele \ "@title").text)
        schema.modules += module
        (ele \ "group").foreach { ele => parseGroup(ele, report, module, name, None) }
      }
    }

    (xml \ "pages" \ "page").foreach { ele =>
      report.addPage(Page((ele \ "@name").text, (ele \ "@iterable").text == "true"))
    }
    report.template = (xml \ "pages" \ "@template").text
    report.extension = (xml \ "pages" \ "@extension").text
    report.imageurl = (xml \ "pages" \ "@imageurl").text

    report.init()
    report
  }

  def parseGroup(node: scala.xml.Node, report: Report, module: Module, groupModuleName: Option[String], parent: Option[Group]): Unit = {
    val name = (node \ "@name").text
    var tables = (node \ "@tables").text
    var mp = groupModuleName
    if (Strings.isBlank(tables)) {
      tables = "@MODULE"
      mp = if (mp.nonEmpty) Some(mp.get + "." + name) else Some(name)
    }
    val group = new Group(name, (node \ "@title").text, module, mp, tables)
    (node \ "image").foreach { ele =>
      val img = new Image((ele \ "@name").text, (ele \ "@title").text, module.schema.name, (ele \ "@tables").text, ele.text.trim)
      val direction = (ele \ "@direction").text
      if (Strings.isNotBlank(direction) && Set("top to bottom", "left to right").contains(direction)) {
        img.direction = Some(direction)
      }
      group.addImage(img)
    }
    parent match {
      case None => module.addGroup(group)
      case Some(p) => p.addGroup(group)
    }

    (node \ "group").foreach { ele => parseGroup(ele, report, module, groupModuleName, Some(group)) }
  }
}

class Report(val database: Database) extends Initializing with Logging {

  var title: String = _

  var system: System = new System

  var schemas: List[ReportSchema] = List()

  var contextPath: String = ""

  var pages: List[Page] = List()

  var template: String = _

  var imageurl: String = _

  var reserveImageUmlScript: Boolean = false

  var extension: String = _

  val table2Group = Collections.newMap[Table, Group]

  def addSchema(schema: ReportSchema): Unit = {
    schemas :+= schema
  }

  def addPage(page: Page): Unit = {
    pages :+= page
  }

  def allGroups: List[Group] = {
    for (s <- schemas; m <- s.modules; g <- m.groups) yield g
  }

  def allTables: List[Table] = {
    for (s <- schemas; m <- s.modules; g <- m.groups; t <- g.tables) yield t
  }

  def allSequences: List[Sequence] = {
    val seqs = for (sc <- database.schemas.values; s <- sc.sequences) yield s
    seqs.toList
  }

  def allImages: List[Image] = {
    for (s <- schemas; m <- s.modules; g <- m.groups; i <- g.allImages) yield i
  }

  def build(): Unit = {
    for (s <- schemas; m <- s.modules; g <- m.groups; t <- g.tables) {
      table2Group.put(t, g)
    }
  }

  def refTableUrl(tableRef: TableRef): String = {
    database.getTable(tableRef) match {
      case None => logger.warn("Cannot find group of [" + tableRef.qualifiedName + "]"); "error"
      case Some(t) =>
        table2Group.get(t) match {
          case None => logger.warn("Cannot find group of [" + tableRef.qualifiedName + "]"); ""
          case Some(g) => contextPath + g.path + s".html#表格-${tableRef.name.value}-${t.comment.getOrElse("")}"
        }
    }
  }

  def tableUrl(table: Table): String = {
    table2Group.get(table) match {
      case None => logger.warn("Cannot find group of [" + table.qualifiedName + "]"); "error"
      case Some(g) => contextPath + g.path + s".html#表格-${table.name.value}-${table.comment.getOrElse("")}"
    }
  }

  def init(): Unit = {
    if (Strings.isEmpty(template)) template = "html"
    if (Strings.isEmpty(title)) title = "数据库结构说明"
    if (Strings.isEmpty(imageurl)) imageurl = "images/"
    else {
      if (!imageurl.endsWith("/")) imageurl += "/"
    }
    if (Strings.isEmpty(extension)) extension = ".html"
  }
}
