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

package org.beangle.db.transport.schema

import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.meta.{Constraint, ForeignKey, PrimaryKey}
import org.beangle.db.transport.Converter

class PrimaryKeyConverter(val source: SchemaWrapper, val target: SchemaWrapper) extends Converter with Logging {

  private val primaryKeys = new collection.mutable.ListBuffer[PrimaryKey]

  def addPrimaryKeys(newPks: Seq[PrimaryKey]): Unit = {
    primaryKeys ++= newPks
  }

  def reset(): Unit = {

  }

  def start(): Unit = {
    val watch = new Stopwatch(true)
    logger.info("Starting apply primary keys...")
    for (pk <- primaryKeys.sorted) {
      val sql = target.engine.alterTable(pk.table).addPrimaryKey(pk)
      try {
        target.executor.update(sql)
        logger.info(s"Apply primary key ${pk.name}")
      } catch {
        case e: Exception => logger.warn(s"Cannot execute $sql")
      }
    }
    logger.info(s"End primary replication,using $watch")
  }
}
