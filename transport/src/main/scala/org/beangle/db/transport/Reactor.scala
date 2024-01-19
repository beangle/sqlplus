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

package org.beangle.db.transport

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.ds.DataSourceUtils
import org.beangle.data.jdbc.engine.StoreCase
import org.beangle.data.jdbc.meta.{Schema, Table}
import org.beangle.db.transport.Config.{TableConfig, Task}
import org.beangle.db.transport.converter.*
import org.beangle.db.transport.converter.TableConverter.TablePair

import java.io.FileInputStream
import java.util.concurrent.LinkedBlockingQueue

object Reactor extends Logging {

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage: Reactor /path/to/your/conversion.xml");
      return
    }
    val xml = scala.xml.XML.load(new FileInputStream(args(0)))
    val reactor = new Reactor(Config(xml))
    reactor.start()
    reactor.close()
  }
}

class Reactor(val config: Config) extends Logging {
  def start() = {
    var sw= new Stopwatch(true)
    config.beforeActions foreach { acf =>
      acf.category match {
        case "script" => new SqlAction(config.source.dataSource, acf.properties("file")).process()
        case _ => logger.warn("Cannot support " + acf.category)
      }
    }

    val converters = new collection.mutable.ListBuffer[Converter]

    val source = new DefaultTableStore(config.source.dataSource, config.source.engine)
    val target = new DefaultTableStore(config.target.dataSource, config.target.engine)
    config.tasks foreach { task =>
      source.loadMetas(task.fromCatalog, task.fromSchema, task.table.buildNameFilter(), true, true)
      target.loadMetas(task.toCatalog, task.toSchema, task.table.buildNameFilter(), true, true)
      target.createSchema(task.toSchema)
    }

    val dataConverter = new TableConverter(source, target, config.maxthreads, config.bulkSize)

    val taskTables = Collections.newMap[Task, Iterable[TablePair]]
    config.tasks foreach { task =>
      val srcSchema = source.getSchema(task.fromCatalog, task.fromSchema)
      val targetSchema = target.getSchema(task.toCatalog, task.toSchema)
      val tables = filterTables(task.table, srcSchema, targetSchema)

      val dataRange = config.dataRange
      val pairs = new LinkedBlockingQueue[TablePair]
      ThreadWorkers.work(tables, p => {
        val srcCount = source.count(p._1)
        if (dataRange._1 <= srcCount && srcCount <= dataRange._2) {
          pairs.add(TablePair(p._1, p._2, srcCount))
        }
      }, config.maxthreads)
      import scala.jdk.CollectionConverters.*
      taskTables.put(task, pairs.asScala)
      dataConverter.add(pairs.asScala)
    }

    converters += dataConverter

    val pkConverter = new PrimaryKeyConverter(target, config.maxthreads)
    pkConverter.add(dataConverter.primaryKeys)
    converters += pkConverter

    val indexConverter = new IndexConverter(target, config.maxthreads)
    config.tasks foreach { task =>
      if task.table.withIndex then
        indexConverter.add(taskTables(task).flatten(_.target.indexes))
    }
    converters += indexConverter

    val constraintConverter = new ConstraintConverter(target, config.maxthreads)
    config.tasks foreach { task =>
      if task.table.withConstraint then
        constraintConverter.add(taskTables(task).flatten(_.target.foreignKeys))
    }
    converters += constraintConverter

    val sequenceConverter = new SequenceConverter(target)
    config.tasks foreach { task =>
      val srcSchema = source.getSchema(task.fromCatalog, task.fromSchema)
      val sequences = srcSchema.filterSequences(task.sequence.includes, task.sequence.excludes)
      sequences foreach { n =>
        n.schema = target.getSchema(task.toCatalog, task.toSchema)
        if (config.target.engine.storeCase != StoreCase.Mixed) {
          n.toCase(config.target.engine.storeCase == StoreCase.Lower)
        }
        n.attach(config.target.engine)
      }
      sequenceConverter.add(sequences)
    }
    converters += sequenceConverter

    for (converter <- converters) {
      converter.start()
    }

    config.afterActions foreach { acf =>
      acf.category match {
        case "script" => new SqlAction(config.target.dataSource, acf.properties("file")).process()
        case _ => logger.warn("Cannot support " + acf.category)
      }
    }
    logger.info(s"transport complete using ${sw}")
  }

  def close(): Unit = {
    //cleanup
    DataSourceUtils.close(config.source.dataSource)
    DataSourceUtils.close(config.target.dataSource)
  }

  private def filterTables(tableConfig: TableConfig, srcSchema: Schema, targetSchema: Schema): List[Tuple2[Table, Table]] = {
    val tables = srcSchema.filterTables(tableConfig.includes, tableConfig.excludes)
    val tablePairs = Collections.newMap[String, (Table, Table)]

    for (srcTable <- tables) {
      val targetTable = srcTable.clone()
      targetTable.updateSchema(targetSchema)
      tableConfig.lowercase foreach { lowercase =>
        if (lowercase) targetTable.toCase(true)
      }
      targetTable.attach(targetSchema.database.engine)
      tablePairs.put(targetTable.name.toString, srcTable -> targetTable)
    }
    tablePairs.values.toList
  }

}
