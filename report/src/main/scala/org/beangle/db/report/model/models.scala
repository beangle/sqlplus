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

import org.beangle.commons.lang.Strings
import org.beangle.commons.regex.AntPathPattern
import org.beangle.data.jdbc.meta.{Identifier, Table}

case class Page(val name: String, val iterable: Boolean)

trait TableContainer {
  val patterns: Array[AntPathPattern]
  val tables = new collection.mutable.ListBuffer[Table]

  def matches(table: Table): Boolean = {
    val lowertable = table.name.value.toLowerCase
    patterns.exists(p => p.matches(lowertable))
  }

  def contains(tableName: Identifier): Boolean = {
    tables.exists(t => t.name.toCase(true) == tableName)
  }

  def addTable(table: Table): Unit = {
    tables += table
  }
}

class Image(val name: String, val title: String, tableseq: String, val description: String) extends TableContainer {
  override val patterns = Strings.split(tableseq.toLowerCase, ",").map(new AntPathPattern(_))

  def select(alltables: collection.Iterable[Table]): Unit = {
    for (table <- alltables) {
      if (matches(table)) addTable(table)
    }
  }
}

class Module(val name: String, val title: String, tableseq: String, moduleCode: String) extends TableContainer {
  val content = new StringBuffer()
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

  override def matches(table: Table): Boolean = {
    if (null != moduleCode) {
      if (tableseq == "@MODULE") {
        table.module exists (m => m == moduleCode)
      } else {
        val inmodule = table.module exists (m => m.startsWith(moduleCode))
        if (inmodule) {
          parent match {
            case Some(pm) => pm.matches(table) && super.matches(table)
            case None => super.matches(table)
          }
        } else {
          false
        }
      }
    } else {
      parent match {
        case Some(pm) => pm.matches(table) && super.matches(table)
        case None => super.matches(table)
      }
    }

  }

  def allImages: List[Image] = {
    val buf = new collection.mutable.ListBuffer[Image]
    buf ++= images
    for (module <- children) buf ++= module.allImages
    buf.toList
  }

  def filter(alltables: collection.mutable.Set[Table]): Unit = {
    for (module <- children) module.filter(alltables)
    for (table <- alltables) if (matches(table)) addTable(table)
    alltables --= tables
  }
}

class System {
  var name: String = _
  var version: String = _
  val properties = new java.util.Properties
}
