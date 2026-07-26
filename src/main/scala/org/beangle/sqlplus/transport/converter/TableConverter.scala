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
import org.beangle.jdbc.meta.{PrimaryKey, Table}
import org.beangle.sqlplus.SqlplusLogger
import org.beangle.sqlplus.transport.{Converter, Dataflow, StageReport, StageResult, TableStore}

import java.util.concurrent.ConcurrentHashMap
object TableConverter {
  val zero = '\u0000'

  class TransferException(val transferredRows: Long, val expectedRows: Long, cause: Throwable)
    extends RuntimeException(Option(cause.getMessage).getOrElse(cause.getClass.getSimpleName), cause)

  /** remove zero char in string
   *
   * @param b
   * @return
   */
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
                     val bulkSize: Int) extends Converter {

  private val tablesMap = Collections.newMap[String, Dataflow]

  override def payloadCount: Int = tablesMap.size

  var enableSanitize = checkEncoding()

  private def checkEncoding(): Boolean = {
    target.encoding == "utf8" && target.encoding != source.encoding
  }

  def add(pairs: Iterable[Dataflow]): Unit = {
    pairs.foreach { t =>
      tablesMap.put(t.target.qualifiedName, t)
    }
  }

  def primaryKeys: List[PrimaryKey] = {
    tablesMap.values.flatten(_.target.primaryKey).toList
  }

  def reset(): Unit = {
  }

  def start(): StageResult = {
    val watch = new Stopwatch(true)
    val flows = tablesMap.values.toBuffer.sortBy(_.total).reverse
    val tableCount = flows.length
    val report = new StageReport("tables", tableCount)
    val unavailable = ConcurrentHashMap.newKeySet[String]()

    //clean all table foreign keys
    Workers.workOn(flows, threads) { p =>
      target.cleanForeignKeys(p.target)
    }

    //prepare and recreate table when necessary,don't clean data
    Workers.workOn(flows, threads) { p =>
      try {
        target.clean(p.target)
      } catch {
        case e: Exception =>
          unavailable.add(p.target.qualifiedName)
          report.failed(p.target.qualifiedName, e)
          SqlplusLogger.error(s"Prepare table ${p.target.qualifiedName} failed", e)
      }
    }

    SqlplusLogger.info(s"Start $tableCount tables data replication in $threads threads...")
    //按照数量降序进行同步，数据量越大的，越早开始
    Workers.workOn(flows, threads) { flow =>
      if (!unavailable.contains(flow.target.qualifiedName)) {
        try {
          convert(flow)
          report.succeeded(flow.target.qualifiedName)
        } catch {
          // Each save commits one batch. A later failure therefore leaves a
          // partial target table and must retain the last committed row count.
          case e: TableConverter.TransferException =>
            if e.transferredRows > 0 then
              report.partial(flow.target.qualifiedName, e, e.transferredRows, e.expectedRows)
            else
              report.failed(flow.target.qualifiedName, e, e.transferredRows, e.expectedRows)
            SqlplusLogger.error(
              s"Insert error ${flow.target.qualifiedName} after ${e.transferredRows}/${e.expectedRows} rows", e)
          case e: Exception =>
            report.failed(flow.target.qualifiedName, e)
            SqlplusLogger.error(s"Insert error ${flow.target.qualifiedName}", e)
        }
      }
    }
    SqlplusLogger.info(s"Finish $tableCount tables data replication,using $watch")
    report.result
  }

  def convert(pair: Dataflow): Unit = {
    val targetTable = pair.target
    var committed = 0
    try {
      target.truncate(targetTable)

      val dataIter = source.select(pair.src, pair.where)
      var data = Collections.newBuffer[Array[Any]]
      var finished = 0
      var batchIndex = 0
      try {
        while (dataIter.hasNext) {
          data += dataIter.next()
          finished += 1
          if (finished % bulkSize == 0) {
            insert(targetTable, data, finished, pair.total, batchIndex)
            committed = finished
            batchIndex += 1
            data = Collections.newBuffer[Array[Any]]
          }
        }
        if (data.nonEmpty) {
          insert(targetTable, data, finished, pair.total, batchIndex)
          committed = finished
        }
      } finally {
        dataIter.close()
      }
      if (committed != pair.total) {
        throw new IllegalStateException(s"Source row count changed from ${pair.total} to $committed")
      }
      SqlplusLogger.info(s"Insert $targetTable($committed)")
    } catch {
      case e: Exception => throw new TableConverter.TransferException(committed, pair.total, e)
    }
  }

  def insert(targetTable: Table, data: collection.Seq[Array[Any]], finished: Int, total: Int, batchIndex: Int): Unit = {
    val sw = new Stopwatch(true)
    if (enableSanitize) {
      data foreach { d =>
        d.indices foreach { i => d(i) = TableConverter.sanitize(d(i)) }
      }
    }
    target.save(targetTable, data)
    if (batchIndex == 0 && finished >= total) {
      SqlplusLogger.info(s"Insert $targetTable($finished) in ${sw}")
    } else {
      SqlplusLogger.info(s"Insert $targetTable($finished/$total) in ${sw}")
    }
  }
}
