package org.beangle.db.report.model

import org.beangle.commons.collection.Collections
import org.beangle.data.jdbc.meta.Table

import scala.collection.mutable

class Module(val schema: Schema, val name: Option[String], val title: String) {

  var groups: mutable.Buffer[Group] = Collections.newBuffer[Group]

  def id: String = {
    schema.name + name.map("." + _).getOrElse("")
  }

  def path: String = {
    val packageName = name.map("/" + _).getOrElse("")
    "/" + schema.name + packageName
  }

  def addGroup(g: Group): Unit = {
    groups += g
  }

  var tables: Iterable[Table] = _

  def images: List[Image] = {
    val buf = new collection.mutable.ListBuffer[Image]
    for (g <- groups) buf ++= g.allImages
    buf.toList
  }

  def findGroup(table: Table): Option[Group] = {
    groups.find { m => m.tables.contains(table) } match {
      case Some(m) => Some(m)
      case None => None
    }
  }

}
