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

import org.beangle.commons.bean.Initializing
import org.beangle.commons.io.Files
import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.ds.{DataSourceFactory, DataSourceUtils}
import org.beangle.data.jdbc.meta._
import org.beangle.db.report.model.{Schema => ReportSchema}

import java.io.{File, FileInputStream}

object Report {

  def apply(reportXml: String): Report = {
    val xml = scala.xml.XML.load(new FileInputStream(reportXml))
    val dir = new File(reportXml).getParent
    var database: Database = null
    if ((xml \ "db").nonEmpty) {
      val dbconf = DataSourceUtils.parseXml(xml)
      database = new Database(dbconf.engine)
      val ds = DataSourceFactory.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props)
      val schema = new Schema(database, dbconf.schema)

      val meta = ds.getConnection().getMetaData
      val loader = new MetadataLoader(meta, dbconf.engine)
      loader.loadTables(schema, extras = true)
      loader.loadSequences(schema)
      DataSourceUtils.close(ds)
    } else {
      val databaseXml = (xml \ "database" \ "@xml").text
      database = Serializer.fromXml(Files.readString(new File(dir + Files./ + databaseXml)))
    }
    val report = new Report(database)
    report.title = (xml \ "@title").text
    report.contentPath = (xml \ "@contentPath").text
    report.system.name = (xml \ "system" \ "@name").text
    report.system.version = (xml \ "system" \ "@version").text
    (xml \ "system" \ "props" \ "prop").foreach { ele => report.system.properties.put((ele \ "@name").text, (ele \ "@value").text) }

    (xml \ "schemas" \ "schema").foreach { ele =>
      val schema = new ReportSchema((ele \ "@name").text, (ele \ "@title").text,report)
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
      group.addImage(
        new Image((ele \ "@name").text, (ele \ "@title").text, module.schema.name, (ele \ "@tables").text, ele.text.trim))
    }
    parent match {
      case None => module.addGroup(group)
      case Some(p) => p.addGroup(group)
    }

    (node \ "group").foreach { ele => parseGroup(ele, report, module, groupModuleName, Some(group)) }
  }
}

class Report(val database: Database) extends Initializing {

  var title: String = _

  var system: System = new System

  var schemas: List[ReportSchema] = List()

  var contentPath: String = ""

  var pages: List[Page] = List()

  var template: String = _

  var imageurl: String = _

  var extension: String = _

  def addSchema(schema: ReportSchema): Unit = {
    schemas :+= schema
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
