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

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.ThreadTasks
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.meta.Table
import org.beangle.db.transport.{ConversionModel, Converter, DataWrapper}

import java.util.concurrent.LinkedBlockingQueue
import scala.collection.mutable.ListBuffer

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

class TableConverter(val source: DataWrapper, val target: DataWrapper, val threads: Int,
                     val bulkSize: Int, val dataRange: (Int, Int),
                     val model: ConversionModel.Value) extends Converter with Logging {

  val tables = new ListBuffer[(Table, Table)]

  protected def addTable(pair: (Table, Table)): Unit = {
    tables += pair
  }

  def addAll(pairs: Seq[(Table, Table)]): Unit = {
    tables ++= pairs
  }

  def reset(): Unit = {
  }

  def start(): Unit = {
    val watch = new Stopwatch(true)
    val tableCount = tables.length
    //set up all target table
    tables.foreach { case (src, tar) => target.cleanForeignKeys(tar) }
    tables.foreach { case (src, tar) => target.clean(tar) }

    val buffer = new LinkedBlockingQueue[(Table, Table)]
    import scala.jdk.CollectionConverters.*
    buffer.addAll(tables.sortWith(_._1.name > _._1.name).asJava)
    logger.info(s"Start $tableCount tables data replication in $threads threads...")
    ThreadTasks.start(new ConvertTask(source, target, buffer), threads)
    logger.info(s"End $tableCount tables data replication,using $watch")
  }

  class ConvertTask(val source: DataWrapper, val target: DataWrapper, val buffer: LinkedBlockingQueue[Tuple2[Table, Table]]) extends Runnable {

    def run(): Unit = {
      while (!buffer.isEmpty) {
        try {
          val p = buffer.poll()
          if (null != p) convert(p)
        } catch {
          case e: IndexOutOfBoundsException =>
          case e: Exception => logger.error("Error in convertion ", e)
        }
      }
    }

    private def processTable(table: Table, datacount: Int): Boolean = {
      if (datacount < dataRange._1 || dataRange._2 < datacount) {
        logger.info(s"Ignore table ${table.name} for count ${datacount}")
        false
      } else {
        if model == ConversionModel.Recreate || target.count(table) != datacount then
          target.truncate(table)
          true
        else
          false
      }
    }

    def convert(pair: (Table, Table)): Unit = {
      val srcTable = pair._1
      val targetTable = pair._2
      try {
        val count = source.count(srcTable)
        if (!processTable(targetTable, count)) return

          if (count == 0) {
            target.save(targetTable, List.empty)
            logger.info(s"Insert $targetTable(0)")
          } else {
            val dataIter = source.select(srcTable)
            val data = Collections.newBuffer[Array[Any]]
            var curr = 0
            try {
              while (curr < count && dataIter.hasNext) {
                data += dataIter.next()
                curr += 1
                if (curr % 10000 == 0) {
                  insert(targetTable, data, curr, count)
                  data.clear()
                }
              }
              if (data.nonEmpty) {
                insert(targetTable, data, curr, count)
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
  }

  def insert(targetTable: Table, data: collection.Seq[Array[Any]], current: Int, total: Int): Unit = {
    data foreach { d =>
      d.indices foreach { i =>
        d(i) = TableConverter.sanitize(d(i))
      }
    }
    target.save(targetTable, data)
    val name = Thread.currentThread().getName
    logger.info(s"$name Insert $targetTable($current/$total)")
  }
}
