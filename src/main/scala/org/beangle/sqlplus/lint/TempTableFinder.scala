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

package org.beangle.sqlplus.lint

import org.beangle.commons.collection.Collections
import org.beangle.jdbc.meta.Schema.NameFilter
import org.beangle.jdbc.meta.{Database, Table}

object TempTableFinder {

  def find(database: Database, pattern: String): Seq[String] = {
    val filter = NameFilter(pattern)
    val tmpList = Collections.newBuffer[String]
    database.schemas.values.foreach { schema =>
      tmpList ++= filter.filter(schema.tables.values.map(x => x.name)).map(Table.qualify(schema, _))
    }
    tmpList.sorted.toSeq
  }
}
