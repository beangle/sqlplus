/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.db.report.model

import scala.Array.canBuildFrom

import org.beangle.data.jdbc.meta.Table
import org.beangle.commons.lang.Strings
import org.beangle.commons.text.regex.AntPathPattern

case class Page(val name: String, val iterator: String)

trait TableContainer {
  val patterns: Array[AntPathPattern]
  val tables = new collection.mutable.ListBuffer[Table]

  def matches(tableName: String): Boolean = {
    val lowertable = tableName.toLowerCase
    patterns.exists(p => p.matches(lowertable))
  }

  def addTable(table: Table): Unit = {
    tables += table
  }
}

class Image(val name: String, val title: String, tableseq: String, val description: String) extends TableContainer {
  override val patterns = Strings.split(tableseq.toLowerCase, ",").map(new AntPathPattern(_))

  def select(alltables: collection.Iterable[Table]) {
    for (table <- alltables) {
      if (matches(table.name.value)) addTable(table)
    }
  }
}

class Module(val name: String, val title: String, tableseq: String) extends TableContainer {
  val content = new StringBuffer();
  override val patterns = Strings.split(tableseq.toLowerCase, ",").map(new AntPathPattern(_))
  var children: List[Module] = List.empty
  var images: List[Image] = List.empty
  var parent: Option[Module] = None

  def addImage(image: Image): Unit = {
    images :+= image
  }

  override def toString: String = {
    path + " tables(" + tables.size + ")"
  }

  def path: String = {
    if (parent.isEmpty) name else (parent.get.path + "/") + name
  }

  def addModule(module: Module): Unit = {
    children :+= module
    module.parent = Some(this)
  }

  override def matches(tableName: String): Boolean = {
    parent match {
      case Some(pm) => pm.matches(tableName) && super.matches(tableName)
      case None => super.matches(tableName)
    }
  }

  def allImages: List[Image] = {
    val buf = new collection.mutable.ListBuffer[Image]
    buf ++= images
    for (module <- children) buf ++= module.allImages
    buf.toList
  }

  def filter(alltables: collection.mutable.Set[Table]) {
    for (module <- children) module.filter(alltables)
    for (table <- alltables) if (matches(table.name.value)) addTable(table)
    alltables --= tables
  }
}

class System {
  var name: String = _
  var version: String = _
  val properties = new java.util.Properties
}
