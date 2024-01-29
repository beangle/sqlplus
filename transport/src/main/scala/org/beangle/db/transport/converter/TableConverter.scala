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
import org.beangle.data.jdbc.meta.{Constraint, PrimaryKey, Table}
import org.beangle.db.transport.{Converter, Dataflow, TableStore}

object TableConverter {
  val zero = '\u0000'

  def sanitize(b: Any): Any = {
    b match {
      case s: String =>
        var zeroIdx = s.indexOf(zero)
        if (zeroIdx > -1) {
          val sb = new StringBuilder(s)
          while (zeroIdx > -1) {
            sb.deleteCharAt(zeroIdx)
            zeroIdx = sb.indexOf(zero)
          }
          sb.toString
        } else {
          s
        }
      case _ => b
    }
  }

}

class TableConverter(val source: TableStore, val target: TableStore, val threads: Int,
                     val bulkSize: Int) extends Converter with Logging {

  private val tablesMap = Collections.newMap[String, Dataflow]

  override def payloadCount: Int = tablesMap.size

  var enableSanitize = false

  def add(pairs: Iterable[Dataflow]): Unit = {
    pairs.foreach { t =>
      tablesMap.put(t.target.qualifiedName, t)
    }
  }

  def primaryKeys: List[PrimaryKey] = {
    tablesMap.values.flatten(_.target.primaryKey).toList
  }

  def constraints: List[Constraint] = {
    tablesMap.values.flatten(_.target.foreignKeys).toList
  }

  def reset(): Unit = {
  }

  def start(): Unit = {
    val watch = new Stopwatch(true)
    val tables = tablesMap.values.toBuffer.sortBy(_.total).reverse
    val tableCount = tables.length

    //clean all table foreign keys
    Workers.work(tables, p => {
      target.cleanForeignKeys(p.target)
    }, threads)

    //prepare and recreate table when necessary,don't clean data
    Workers.work(tables, p => {
      target.clean(p.target)
    }, threads)

    logger.info(s"Start $tableCount tables data replication in $threads threads...")
    //按照数量降序进行同步，数据量越大的，越早开始
    Workers.work(tables, tablePair => {
      convert(tablePair)
    }, threads)
    logger.info(s"Finish $tableCount tables data replication,using $watch")
  }

  def convert(pair: Dataflow): Unit = {
    val targetTable = pair.target
    try {
      target.truncate(targetTable)

      if (pair.total == 0) {
        target.save(targetTable, List.empty)
        logger.info(s"Insert $targetTable(0)")
      } else {
        val dataIter = source.select(pair.src, pair.where)
        val data = Collections.newBuffer[Array[Any]]
        var finished = 0
        var batchIndex = 0
        try {
          while (dataIter.hasNext) {
            data += dataIter.next()
            finished += 1
            if (finished % bulkSize == 0) {
              insert(targetTable, data, finished, pair.total, batchIndex)
              batchIndex += 1
              data.clear()
            }
          }
          if (data.nonEmpty) {
            insert(targetTable, data, finished, pair.total, batchIndex)
          }
        } catch {
          case e: Exception => logger.error(s"Insert error ${targetTable.qualifiedName}", e)
        } finally {
          dataIter.close()
        }
      }
    } catch {
      case e: Exception => logger.error(s"Insert error ${targetTable.qualifiedName}", e)
    }
  }

  def insert(targetTable: Table, data: collection.Seq[Array[Any]], finished: Int, total: Int, batchIndex: Int): Unit = {
    if (enableSanitize) {
      data foreach { d =>
        d.indices foreach { i =>
          d(i) = TableConverter.sanitize(d(i))
        }
      }
    }
    target.save(targetTable, data)
    if (batchIndex == 0 && finished >= total) {
      logger.info(s"Insert $targetTable($finished)")
    } else {
      logger.info(s"Insert $targetTable($finished/$total)")
    }
  }
}
