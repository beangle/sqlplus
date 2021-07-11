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

import org.beangle.commons.regex.AntPathPattern
import org.beangle.data.jdbc.meta.Table

class Group(val name: String, val title: String, module: Module, groupModuleName: Option[String], tableseq: String)
  extends TableContainer {
  override val patterns: Array[AntPathPattern] = TableContainer.buildPatterns(module.schema.name, tableseq)
  var children: List[Group] = List.empty
  var images: List[Image] = List.empty
  var parent: Option[Group] = None

  def addImage(image: Image): Unit = {
    images :+= image
  }

  override def toString: String = {
    fullName + " tables(" + tables.size + ")"
  }

  def fullName: String = {
    if (parent.isEmpty) name else (parent.get.fullName + "/") + name
  }

  def path: String = {
    val path = module.path + "/" + name
    if (parent.isEmpty) path else (parent.get.fullName + "/") + path
  }

  def addGroup(group: Group): Unit = {
    children :+= group
    group.parent = Some(this)
  }

  override def matches(table: Table): Boolean = {
    if (groupModuleName.isDefined) {
      val prefix = groupModuleName.get
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
    for (g <- children) buf ++= g.allImages
    buf.toList
  }

  def filter(alltables: collection.mutable.Set[Table]): Unit = {
    for (g <- children) g.filter(alltables)
    for (table <- alltables) if (matches(table)) addTable(table)
    alltables --= tables
  }
}
