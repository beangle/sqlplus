/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.db.conversion.schema

import java.util.concurrent.LinkedBlockingQueue

import scala.collection.mutable.ListBuffer

import org.beangle.commons.collection.page.PageLimit
import org.beangle.commons.lang.ThreadTasks
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.db.conversion.{ Converter, DataWrapper }
import org.beangle.data.jdbc.meta.Table

class TableConverter(val source: DataWrapper, val target: DataWrapper, val threads: Int = 5) extends Converter with Logging {

  val tables = new ListBuffer[Tuple2[Table, Table]]

  protected def addTable(pair: Tuple2[Table, Table]) {
    tables += pair
  }

  def addAll(pairs: Seq[Tuple2[Table, Table]]) {
    tables ++= pairs
  }

  def reset() {
  }

  def start() {
    val watch = new Stopwatch(true)
    val tableCount = tables.length
    val buffer = new LinkedBlockingQueue[Tuple2[Table, Table]]
    buffer.addAll(collection.JavaConverters.asJavaCollection(tables.sortWith(_._1.name > _._1.name)))
    logger.info(s"Start $tableCount tables data replication in $threads threads...")
    ThreadTasks.start(new ConvertTask(source, target, buffer), threads)
    logger.info(s"End $tableCount tables data replication,using $watch")
  }

  class ConvertTask(val source: DataWrapper, val target: DataWrapper, val buffer: LinkedBlockingQueue[Tuple2[Table, Table]]) extends Runnable {

    def run() {
      while (!buffer.isEmpty) {
        try {
          val p = buffer.poll()
          if (null != p) convert(p)
        } catch {
          case e: IndexOutOfBoundsException =>
          case e: Exception                 => logger.error("Error in convertion ", e)
        }
      }
    }

    private def createOrReplaceTable(table: Table): Boolean = {
      if (target.drop(table)) {
        if (target.create(table)) {
          logger.info(s"Create table ${table.name}")
          return true
        } else {
          logger.error(s"Create table ${table.name} failure.")
        }
      }
      false
    }

    def convert(pair: Tuple2[Table, Table]) {
      val srcTable = pair._1
      val targetTable = pair._2
      try {
        if (!createOrReplaceTable(targetTable)) return
        var count = source.count(srcTable)

        if (count == 0) {
          target.save(targetTable, List.empty)
          logger.info(s"Insert $targetTable(0)")
        } else {
          if (count >= 600000 && !(source.supportLimit && srcTable.primaryKey != null)) {
            println("Cannot paginate " + targetTable.name + " convertion ignored!")
            return
          }
          var curr = 0
          var pageIndex = 0
          while (curr < count) {
            val limit = new PageLimit(pageIndex + 1, 10000)
            val data = if (source.supportLimit && srcTable.primaryKey != null) source.get(srcTable, limit) else source.get(srcTable)
            var breakable = false
            if (data.isEmpty) {
              logger.error(s"Failure in fetching ${srcTable.name} data ${limit.pageIndex}(${limit.pageSize})")
              if (limit.pageIndex * limit.pageSize >= count) breakable = true
            }
            if (!breakable) {
              val successed = target.save(targetTable, data)
              curr += data.size
              pageIndex += 1
              val name = Thread.currentThread().getName
              if (successed == count) {
                logger.info(s"$name Insert $targetTable($successed)")
              } else if (successed == data.size) {
                logger.info(s"$name Insert $targetTable($curr/$count)")
              } else {
                logger.warn(s"$name Insert $targetTable($successed/${data.size})")
              }
            }
          }
        }
      } catch {
        case e: Exception => logger.error(s"Insert error ${targetTable.qualifiedName}", e)
      }
    }
  }
}
