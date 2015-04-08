/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.db.conversion

import org.beangle.data.jdbc.meta.Table
import org.beangle.commons.collection.page.PageLimit

trait DataWrapper {

  def get(table: Table): Seq[Seq[_]]

  def get(table: Table, limit: PageLimit): Seq[Seq[_]]

  def drop(table: Table): Boolean

  def create(table: Table): Boolean

  def save(table: Table, datas: Seq[Seq[_]]): Int

  def close()

  def count(table: Table): Int

  def supportLimit: Boolean

}