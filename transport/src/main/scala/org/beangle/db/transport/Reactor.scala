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
import org.beangle.data.jdbc.meta.Schema.NameFilter
import org.beangle.data.jdbc.meta.{Schema, Table, View}
import org.beangle.db.transport.Config.*
import org.beangle.db.transport.converter.*

import java.io.{File, FileInputStream}
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
    val sw = new Stopwatch(true)
    executeActions(config.source, config.beforeActions)

    val converters = new collection.mutable.ListBuffer[Converter]

    val source = new DefaultTableStore(config.source.dataSource, config.source.engine)
    val target = new DefaultTableStore(config.target.dataSource, config.target.engine)
    val allFilter = new NameFilter()
    allFilter.include("*")
    config.tasks foreach { task =>
      source.loadMetas(task.fromCatalog, task.fromSchema, task.table.buildNameFilter(), task.view.buildNameFilter())
      //we should load all target object ignore src filter
      //case 1: exclude all table,just transport view.so we need load target table
      target.loadMetas(task.toCatalog, task.toSchema, allFilter, allFilter)
      target.createSchema(task.toSchema)
    }

    val dataConverter = new TableConverter(source, target, config.maxthreads, config.bulkSize)

    val taskTables = Collections.newMap[Task, Iterable[Dataflow]]
    config.tasks foreach { task =>
      val srcSchema = source.getSchema(task.fromCatalog, task.fromSchema)
      val targetSchema = target.getSchema(task.toCatalog, task.toSchema)
      val tables = filterTables(task.table, srcSchema, targetSchema)
      val views = filterViews(task.view, srcSchema, targetSchema)

      val dataRange = config.dataRange
      val pairs = new LinkedBlockingQueue[Dataflow]
      ThreadWorkers.work(tables, p => {
        val where = task.table.getWhere(p._1)
        val total = source.count(p._1, where)
        if (dataRange._1 <= total && total <= dataRange._2) {
          pairs.add(Dataflow(p._1, p._2, where, total))
        }
      }, config.maxthreads)
      ThreadWorkers.work(views, p => {
        val where = task.view.getWhere(p._1)
        val total = source.count(p._1, where)
        if (dataRange._1 <= total && total <= dataRange._2) {
          pairs.add(Dataflow(p._1, p._2, where, total))
        }
      }, config.maxthreads)
      import scala.jdk.CollectionConverters.*
      taskTables.put(task, pairs.asScala)
      dataConverter.add(pairs.asScala)
    }

    converters += dataConverter

    val pks = dataConverter.primaryKeys
    if (pks.nonEmpty) {
      val pkConverter = new PrimaryKeyConverter(target, config.maxthreads)
      pkConverter.add(pks)
      converters += pkConverter
    }

    val indexConverter = new IndexConverter(target, config.maxthreads)
    config.tasks foreach { task =>
      if task.table.withIndex then
        indexConverter.add(taskTables(task).flatten(_.target.indexes))
    }
    if indexConverter.payloadCount > 0 then converters += indexConverter

    val constraintConverter = new ConstraintConverter(target, config.maxthreads)
    config.tasks foreach { task =>
      if task.table.withConstraint then
        constraintConverter.add(taskTables(task).flatten(_.target.foreignKeys))
    }
    if constraintConverter.payloadCount > 0 then converters += constraintConverter

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
    if sequenceConverter.payloadCount > 0 then converters += sequenceConverter

    for (converter <- converters) {
      converter.start()
    }

    executeActions(config.target, config.afterActions)
    logger.info(s"transport complete using ${sw}")
  }

  def close(): Unit = {
    //cleanup
    DataSourceUtils.close(config.source.dataSource)
    DataSourceUtils.close(config.target.dataSource)
  }

  private def executeActions(source: Source, actions: Iterable[ActionConfig]): Unit = {
    actions foreach { acf =>
      acf.category match {
        case "script" =>
          acf.contents match
            case Some(sqls) =>
              logger.info("execute sql scripts")
              SqlAction.execute(source.dataSource, sqls)
            case None =>
              if (acf.properties.contains("file")) {
                val f = new File(acf.properties("file"))
                require(f.exists(), "sql file:" + f.getAbsolutePath + " doesn't exists")
                logger.info("execute sql scripts " + f.getAbsolutePath)
                SqlAction.execute(source.dataSource, f)
              }
        case _ => logger.warn("Cannot support " + acf.category)
      }
    }
  }

  private def filterTables(cfg: TableConfig, srcSchema: Schema, targetSchema: Schema): List[(Table, Table, Option[String])] = {
    val tables = srcSchema.filterTables(cfg.includes, cfg.excludes)
    val tablePairs = Collections.newMap[String, (Table, Table, Option[String])]

    for (src <- tables) {
      val tar = src.clone()
      tar.updateSchema(targetSchema)
      cfg.lowercase foreach { lowercase =>
        if (lowercase) tar.toCase(true)
      }
      tar.attach(targetSchema.database.engine)
      tablePairs.put(tar.name.toString, (src, tar, cfg.getWhere(src)))
    }
    tablePairs.values.toList
  }

  private def filterViews(cfg: ViewConfig, srcSchema: Schema, targetSchema: Schema): List[(View, Table, Option[String])] = {
    val views = srcSchema.filterViews(cfg.includes, cfg.excludes)
    val tablePairs = Collections.newMap[String, (View, Table, Option[String])]

    for (src <- views) {
      val tar = src.toTable
      tar.updateSchema(targetSchema)
      cfg.lowercase foreach { lowercase =>
        if (lowercase) tar.toCase(true)
      }
      tar.attach(targetSchema.database.engine)
      tablePairs.put(tar.name.toString, (src, tar, cfg.getWhere(src)))
    }
    tablePairs.values.toList
  }
}
