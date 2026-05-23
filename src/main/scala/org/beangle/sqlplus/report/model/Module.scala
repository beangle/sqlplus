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

import org.beangle.jdbc.meta.Table

class Module(val schema: Schema, val name: String, val title: String, tablePatterns: String) extends TableContainer {


  var images: List[Image] = List.empty

  override val patterns = TableContainer.buildPatterns(schema.name, if tablePatterns == "@" then "*" else tablePatterns)

  def id: String = {
    schema.name + "." + name
  }

  def path: String = {
    "/" + schema.name + "/" + name
  }

  def addImage(image: Image): Unit = {
    images :+= image
  }

  def filter(alltables: collection.mutable.Set[Table]): Unit = {
    for (table <- alltables) if (matches(table)) addTable(table)
    alltables --= tables
  }

  override def matches(table: Table): Boolean = {
    if (tablePatterns == "@") {
      table.module.exists(_.startsWith(this.name))
    } else {
      super.matches(table)
    }
  }
}
