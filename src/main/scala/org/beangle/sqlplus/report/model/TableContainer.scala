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

import org.beangle.commons.lang.Strings
import org.beangle.commons.regex.AntPathPattern
import org.beangle.jdbc.meta.{Identifier, Table}

object TableContainer {
  def buildPatterns(schema: String, tableSeq: String): Array[AntPathPattern] = {
    val defaultSchema = if (null != schema) schema.toLowerCase() else ""
    Strings.split(tableSeq.toLowerCase, ",").map { x =>
      val tablePattern =
        if (x.contains(".")) {
          Strings.replace(x, ".", "_")
        } else {
          if (Strings.isBlank(defaultSchema)) x else s"${defaultSchema}_$x"
        }
      new AntPathPattern(tablePattern)
    }
  }
}

trait TableContainer {
  val patterns: Array[AntPathPattern]
  val tables = new collection.mutable.ListBuffer[Table]

  def matches(table: Table): Boolean = {
    val tableName = Strings.replace(table.qualifiedName, ".", "_").toLowerCase
    patterns.exists(p => p.matches(tableName))
  }

  def contains(tableName: Identifier): Boolean = {
    tables.exists(t => t.name.toCase(true) == tableName)
  }

  def addTable(table: Table): Unit = {
    tables += table
  }
}
