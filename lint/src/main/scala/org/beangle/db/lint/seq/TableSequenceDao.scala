/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.db.lint.seq

/**
 * @author cheneystar 2008-07-23
 */
trait TableSequenceDao {

  /** 得到所有用户的序列号* */
  def getAllNames(): List[String]

  /** 得到数据库中没有被指定的sequence* */
  def getNoneReferenced(): List[String]

  /**
   * 找到所有错误的sequence
   *
   * @return
   */
  def getInconsistent(): List[TableSequence]

  /**
   * 删除指定的sequence
   *
   * @param sequence_name
   * @return
   */
  def drop(sequence_name: String): Boolean

  def setRelation(relation: SequenceNamePattern);

  def adjust(tableSequence: TableSequence): Long
}
