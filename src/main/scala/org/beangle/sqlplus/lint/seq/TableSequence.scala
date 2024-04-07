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

package org.beangle.sqlplus.lint.seq

class TableSequence {

  var seqName: String = _

  var tableName: String = _

  var idColumnName: String = "id"

  var lastNumber: Long = _

  var maxId: Long = _

  override def toString(): String = {
    val buffer = new StringBuilder();
    buffer.append(seqName).append('(').append(lastNumber).append(')');
    buffer.append("  ");
    if (null == tableName) {
      buffer.append("----");
    } else {
      buffer.append(tableName).append('(').append(maxId).append(')');
    }
    return buffer.toString();
  }
}
