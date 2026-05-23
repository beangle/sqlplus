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
import org.beangle.commons.xml.{Document, Node}
import org.beangle.jdbc.ds.{DataSourceFactory, DataSourceUtils}
import org.beangle.jdbc.engine.Engines
import org.beangle.jdbc.meta.*
import org.beangle.sqlplus.SqlplusLogger
import org.beangle.sqlplus.report.model.Schema as ReportSchema
import org.beangle.sqlplus.util.EncryptDataSourceUtils

import java.io.File

object Report {

  def apply(reportXml: File): Report = {
    val xml = Document.parse(reportXml)
    val dir = reportXml.getParent
    var database: Database = null
    if ((xml \ "db").nonEmpty) {
      val dbElem = (xml \ "db").head
      val dbconf = EncryptDataSourceUtils.parseXml(dbElem)
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
        val name = (ele \ "@name").text
        val t = (ele \ "@title").text
        var tables = (ele \ "@tables").text
        if (Strings.isBlank(tables)) tables = "@"

        val module = new Module(schema, name, t, tables)
        schema.modules += module

        (ele \ "image").foreach { ele =>
          val img = new Image((ele \ "@name").text, (ele \ "@title").text, module.schema.name, (ele \ "@tables").text, ele.text.trim)
          val direction = (ele \ "@direction").text
          if (Strings.isNotBlank(direction) && Set("top to bottom", "left to right").contains(direction)) {
            img.direction = Some(direction)
          }
          module.addImage(img)
        }
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

}

class Report(val database: Database) extends Initializing {

  var title: String = _

  var system: System = new System

  var schemas: List[ReportSchema] = List()

  var contextPath: String = ""

  var pages: List[Page] = List()

  var template: String = _

  var imageurl: String = _

  var reserveImageUmlScript: Boolean = false

  var extension: String = _

  val table2Module = Collections.newMap[String, Module]

  def addSchema(schema: ReportSchema): Unit = {
    schemas :+= schema
  }

  def addPage(page: Page): Unit = {
    pages :+= page
  }


  def allTables: List[Table] = {
    for (s <- schemas; m <- s.modules; t <- m.tables) yield t
  }

  def allSequences: List[Sequence] = {
    val seqs = for (sc <- database.schemas.values; s <- sc.sequences) yield s
    seqs.toList
  }

  def allImages: List[Image] = {
    for (s <- schemas; m <- s.modules; i <- m.images) yield i
  }

  def build(): Unit = {
    for (s <- schemas; m <- s.modules; t <- m.tables) {
      table2Module.put(t.qualifiedName, m)
    }
  }

  def refTableUrl(tableRef: TableRef): String = {
    database.getTable(tableRef) match {
      case None => SqlplusLogger.warn("Cannot find refer of [" + tableRef.qualifiedName + "]"); "error"
      case Some(t) =>
        table2Module.get(t.qualifiedName) match {
          case None => SqlplusLogger.warn("Cannot find module of [" + tableRef.qualifiedName + "]"); ""
          case Some(g) => contextPath + g.path + s".html#${tableRef.name.value.replace('_', '-')}"
        }
    }
  }

  def tableUrl(table: Table): String = {
    table2Module.get(table.qualifiedName) match {
      case None => SqlplusLogger.warn("Cannot find group of [" + table.qualifiedName + "]"); "error"
      case Some(g) => contextPath + g.path + s".html#${table.name.value.replace('_', '-')}"
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
