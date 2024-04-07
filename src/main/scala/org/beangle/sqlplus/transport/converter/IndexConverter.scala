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

package org.beangle.sqlplus.transport.converter

import org.beangle.commons.collection.Collections
import org.beangle.commons.concurrent.Workers
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.jdbc.meta.Index
import org.beangle.sqlplus.transport.Converter

class IndexConverter(val target: DefaultTableStore, val threads: Int) extends Converter with Logging {

  private val idxMap = Collections.newMap[String, Index]

  override def payloadCount: Int = idxMap.size

  def add(indxes: Iterable[Index]): Unit = {
    indxes.foreach(x => idxMap.put(x.literalName, x))
  }

  def reset(): Unit = {
  }

  def start(): Unit = {
    val indexes = idxMap.values
    val indexCount = indexes.size
    logger.info(s"Start $indexCount indexes replication in $threads threads...")
    val watch = new Stopwatch(true)
    Workers.work(indexes, index => {
      try {
        target.executor.update(target.engine.createIndex(index))
        logger.info(s"Create index ${index.name}")
      } catch {
        case e: Exception => logger.error(s"Cannot create index ${index.name}", e)
      }
    }, threads)
    logger.info(s"Finish $indexCount indexes replication,using $watch")
  }

}
