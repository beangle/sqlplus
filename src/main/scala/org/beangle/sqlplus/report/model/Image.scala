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
import org.beangle.jdbc.meta.Database

class Image(val name: String, val title: String, schemaName: String, tableseq: String, val description: String) extends TableContainer {
  override val patterns: Array[AntPathPattern] = TableContainer.buildPatterns(schemaName, tableseq)

  var direction: Option[String] = None

  def select(database: Database): Unit = {
    for (schema <- database.schemas.values) {
      for (table <- schema.tables.values) {
        if (matches(table)) addTable(table)
      }
    }
  }
}
