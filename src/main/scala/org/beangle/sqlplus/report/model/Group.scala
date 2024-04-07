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

import org.beangle.commons.regex.AntPathPattern
import org.beangle.jdbc.meta.Table

class Group(val name: String, val title: String, val module: Module, groupModuleName: Option[String], tableseq: String)
  extends TableContainer {

  def id: String = {
    module.id + "." + name
  }

  override val patterns: Array[AntPathPattern] = TableContainer.buildPatterns(module.schema.name, tableseq)
  var children: List[Group] = List.empty
  var images: List[Image] = List.empty

  def addImage(image: Image): Unit = {
    images :+= image
  }

  override def toString: String = {
    fullName + " tables(" + tables.size + ")"
  }

  def fullName: String = {
    name
  }

  def path: String = {
    module.path + "/" + name
  }

  def addGroup(group: Group): Unit = {
    children :+= group
  }

  override def matches(table: Table): Boolean = {
    if (groupModuleName.isDefined) {
      val prefix = groupModuleName.get
      if (tableseq == "@MODULE") {
        table.module contains prefix
      } else {
        val inmodule = table.module exists (m => m.startsWith(prefix))
        inmodule && super.matches(table)
      }
    } else {
      super.matches(table)
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
