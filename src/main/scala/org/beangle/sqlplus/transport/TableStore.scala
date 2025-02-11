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

package org.beangle.sqlplus.transport

import org.beangle.jdbc.engine.Engine
import org.beangle.jdbc.meta.{Relation, Table}
import org.beangle.jdbc.query.ResultSetIterator

trait TableStore {

  def select(r: Relation, where: Option[String]): ResultSetIterator

  def get(table: Table): Option[Table]

  /** 清空所有依赖和索引，但不清除数据
   *
   * @param table
   * @return
   */
  def clean(table: Table): Boolean

  def cleanForeignKeys(table: Table): Unit

  def has(table: Table): Boolean

  def truncate(table: Table): Boolean

  def drop(table: Table): Boolean

  def create(table: Table): Boolean

  def save(table: Table, datas: collection.Seq[Array[_]]): Int

  def close(): Unit

  def count(table: Relation, where: Option[String]): Int

  def engine: Engine

  def encoding: String
}
