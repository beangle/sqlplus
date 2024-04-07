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

import org.beangle.commons.collection.Collections
import org.beangle.jdbc.meta.Table

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

}
