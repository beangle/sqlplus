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
import org.beangle.data.jdbc.meta.Table
import org.beangle.db.transport.Converter

class IndexConverter(val source: SchemaWrapper, val target: SchemaWrapper) extends Converter with Logging {

  val tables = new collection.mutable.ListBuffer[Table]

  def reset(): Unit = {
  }

  def start(): Unit = {
    val watch = new Stopwatch(true)
    var indexCount = 0
    val engine = target.engine
    val executor = target.executor
    for (table <- tables) {
      for (index <- table.indexes) {
        try {
          val isPK = table.primaryKey match {
            case None => false
            case Some(pk) => index.name == pk.name
          }
          indexCount += 1
          if (isPK) {
            executor.update(engine.alterTableAddPrimaryKey(table, table.primaryKey.get))
          } else {
            executor.update(engine.createIndex(index))
          }
          logger.info(s"Create index ${index.name}")
        } catch {
          case e: Exception =>
            logger.error(s"Cannot create index ${index.name}", e)
        }
      }
    }
    logger.info(s"End $indexCount indexes conversion,using $watch")
  }

}
