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

package org.beangle.db.transport.converter

import org.beangle.commons.collection.Collections
import org.beangle.commons.concurrent.Workers
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.meta.PrimaryKey
import org.beangle.db.transport.Converter

class PrimaryKeyConverter(val target: DefaultTableStore, threads: Int) extends Converter with Logging {

  private val primaryKeyMap = Collections.newMap[String, PrimaryKey]

  def add(newPks: Iterable[PrimaryKey]): Unit = {
    newPks.foreach { pk => primaryKeyMap.put(pk.literalName, pk) }
  }

  override def payloadCount: Int = primaryKeyMap.size
  def reset(): Unit = {
  }

  def start(): Unit = {
    val watch = new Stopwatch(true)
    val pks = primaryKeyMap.values
    logger.info(s"Start ${pks.size} primary keys replication in $threads threads...")
    Workers.work(pks, pk => {
      val sql = target.engine.alterTable(pk.table).addPrimaryKey(pk)
      try {
        target.executor.update(sql)
        logger.info(s"Apply ${pk.name}(${pk.table.qualifiedName})")
      } catch {
        case e: Exception => logger.warn(s"Cannot execute $sql")
      }
    }, threads)
    logger.info(s"Finish ${pks.size} primary keys replication,using $watch")
  }

}
