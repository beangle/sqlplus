/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.db.report.model

import java.io.{File, FileInputStream}

import org.beangle.commons.bean.Initializing
import org.beangle.commons.io.Files
import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.ds.DataSourceUtils
import org.beangle.data.jdbc.meta._

object Report {

  def apply(reportXml: String): Report = {
    val xml = scala.xml.XML.load(new FileInputStream(reportXml))
    val dir = new File(reportXml).getParent
    var database: Database = null
    var schemaName: String = null
    if ((xml \ "db").nonEmpty) {
      val dbconf = DataSourceUtils.parseXml(xml)
      database = new Database(dbconf.engine)
      val ds = DataSourceUtils.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props)
      val schema = new Schema(database, dbconf.schema)

      val meta = ds.getConnection().getMetaData()
      val loader = new MetadataLoader(meta, dbconf.engine)
      loader.loadTables(schema, true)
      loader.loadSequences(schema)
      schemaName = dbconf.schema.value
      DataSourceUtils.close(ds)
    } else {
      val databaseXml = (xml \ "database" \ "@xml").text
      schemaName = (xml \ "database" \ "@schema").text
      database = Serializer.fromXml(Files.readString(new File(dir + Files./ + databaseXml)))
    }
    val report = new Report(database, schemaName)
    report.title = (xml \ "@title").text
    report.system.name = (xml \ "system" \ "@name").text
    report.system.version = (xml \ "system" \ "@version").text
    (xml \ "system" \ "props" \ "prop").foreach { ele => report.system.properties.put((ele \ "@name").text, (ele \ "@value").text) }

    (xml \ "modules" \ "module").foreach { ele => parseModule(ele, report, None) }
    (xml \ "pages" \ "page").foreach { ele =>
      report.addPage(
        new Page((ele \ "@name").text, (ele \ "@iterable").text == "true"))
    }
    report.template = (xml \ "pages" \ "@template").text
    report.extension = (xml \ "pages" \ "@extension").text
    report.imageurl = (xml \ "pages" \ "@imageurl").text
    report.init()
    report
  }

  def parseModule(node: scala.xml.Node, report: Report, parent: Option[Module]): Unit = {
    val module = new Module((node \ "@name").text, (node \ "@title").text, (node \ "@tables").text)
    (node \ "image").foreach { ele =>
      module.addImage(
        new Image((ele \ "@name").text, (ele \ "@title").text, (ele \ "@tables").text, ele.text.trim))
    }
    parent match {
      case None => report.addModule(module)
      case Some(p) => p.addModule(module)
    }

    (node \ "module").foreach { ele => parseModule(ele, report, Some(module)) }
  }
}

class Report(val database: Database, val schemaName: String) extends Initializing {

  var title: String = _

  var system: System = new System

  var modules: List[Module] = List()

  var pages: List[Page] = List()

  var template: String = _

  var imageurl: String = _

  var extension: String = _

  var tables: Iterable[Table] = _

  def findModule(table: Table): Option[Module] = {
    modules.find { m => m.tables.contains(table) } match {
      case Some(m) => Some(m)
      case None => None
    }
  }

  def images: List[Image] = {
    val buf = new collection.mutable.ListBuffer[Image]
    for (module <- modules) buf ++= module.allImages
    buf.toList
  }

  def addModule(module: Module): Unit = {
    modules :+= module
  }

  def addPage(page: Page): Unit = {
    pages :+= page
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
