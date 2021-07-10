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

import org.beangle.data.jdbc.meta.Table

class Module(val name: String, val title: String, schema: String, packageName: Option[String], tableseq: String)
  extends TableContainer {
  val content = new StringBuffer()
  override val patterns = TableContainer.buildPatterns(schema, tableseq)
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
    if (packageName.isDefined) {
      val prefix = packageName.get
      if (tableseq == "@MODULE") {
        table.module contains prefix
      } else {
        val inmodule = table.module exists (m => m.startsWith(prefix))
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
