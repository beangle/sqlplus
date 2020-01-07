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
package org.beangle.db.transport

import org.beangle.commons.collection.page.PageLimit
import org.beangle.data.jdbc.meta.Table

trait DataWrapper {

  def get(table: Table): collection.Seq[Array[Any]]

  def get(table: Table, limit: PageLimit): collection.Seq[Array[Any]]

  def has(table: Table): Boolean

  def drop(table: Table): Boolean

  def create(table: Table): Boolean

  def save(table: Table, datas: collection.Seq[Array[_]]): Int

  def close(): Unit

  def count(table: Table): Int

  def supportLimit: Boolean

}
