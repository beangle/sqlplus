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

package org.beangle.sqlplus.transport

import org.beangle.commons.collection.Collections
import org.beangle.commons.concurrent.Workers
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.jdbc.ds.{DataSourceUtils, Source}
import org.beangle.jdbc.engine.StoreCase
import org.beangle.jdbc.meta.*
import org.beangle.jdbc.meta.Schema.NameFilter
import org.beangle.sqlplus.SqlplusLogger
import org.beangle.sqlplus.transport.Config.*
import org.beangle.sqlplus.transport.converter.*

import java.io.{File, FileInputStream}
import java.util.concurrent.LinkedBlockingQueue

object Reactor {

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage: Reactor /path/to/your/transport.xml")
      return
    }
    val workdir = new File(args(0)).getAbsoluteFile.getParent
    val reactor = new Reactor(Config(workdir, new FileInputStream(args(0))))
    val success = try reactor.start() finally reactor.close()
    System.exit(if success then 0 else 1)
  }
}

class Reactor(val config: Config) {
  def start(): Boolean = {
    val sw = new Stopwatch(true)
    val results = Collections.newBuffer[StageResult]
    executeActions(config.source, config.beforeActions)

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
      val scanReport = new StageReport(s"scan ${task.fromSchema.value}", tables.size + views.size)

      val dataRange = config.dataRange
      val pairs = new LinkedBlockingQueue[Dataflow]
      Workers.workOn(tables, config.maxthreads) { p =>
        try {
          val where = task.table.getWhere(p._1)
          val total = source.count(p._1, where)
          if (dataRange._1 <= total && total <= dataRange._2) {
            pairs.add(Dataflow(p._1, p._2, where, total))
          }
          scanReport.succeeded(p._1.qualifiedName)
        } catch {
          case e: Exception =>
            scanReport.failed(p._1.qualifiedName, e)
            SqlplusLogger.error(s"Scan table ${p._1.qualifiedName} failed", e)
        }
      }
      Workers.workOn(views, config.maxthreads) { p =>
        try {
          val where = task.view.getWhere(p._1)
          val total = source.count(p._1, where)
          if (dataRange._1 <= total && total <= dataRange._2) {
            pairs.add(Dataflow(p._1, p._2, where, total))
          }
          scanReport.succeeded(p._1.qualifiedName)
        } catch {
          case e: Exception =>
            scanReport.failed(p._1.qualifiedName, e)
            SqlplusLogger.error(s"Scan view ${p._1.qualifiedName} failed", e)
        }
      }
      results += scanReport.result
      import scala.jdk.CollectionConverters.*
      taskTables.put(task, pairs.asScala)
      dataConverter.add(pairs.asScala)
    }

    val dataResult = dataConverter.start()
    results += dataResult

    // Cleanup may have removed only part of a failed table's structure.
    // Best-effort recovery therefore attempts every selected key and index;
    // individual DDL failures are collected without stopping later objects.
    val pks = dataConverter.primaryKeys
    if (pks.nonEmpty) {
      val pkConverter = new PrimaryKeyConverter(target, config.maxthreads)
      pkConverter.add(pks)
      results += pkConverter.start()
    }

    val indexConverter = new IndexConverter(target, config.maxthreads)
    config.tasks foreach { task =>
      if task.table.withIndex then
        indexConverter.add(taskTables(task).flatten(_.target.indexes))
    }
    if indexConverter.payloadCount > 0 then results += indexConverter.start()

    val uniqueKeyConverter = new UniqueKeyConverter(target, config.maxthreads)
    config.tasks foreach { task =>
      if task.table.withConstraint then
        uniqueKeyConverter.add(taskTables(task).flatten(_.target.uniqueKeys))
    }
    if uniqueKeyConverter.payloadCount > 0 then results += uniqueKeyConverter.start()

    // Foreign keys are last because their referenced key may be either a
    // primary key or a unique key.
    val constraintConverter = new ConstraintConverter(target, config.maxthreads)
    config.tasks foreach { task =>
      if task.table.withConstraint then
        constraintConverter.add(taskTables(task).flatten(_.target.foreignKeys))
    }
    if constraintConverter.payloadCount > 0 then results += constraintConverter.start()

    val sequenceConverter = new SequenceConverter(target)
    config.tasks foreach { task =>
      val srcSchema = source.getSchema(task.fromCatalog, task.fromSchema)
      if (null != task.sequence) {
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
    }
    if sequenceConverter.payloadCount > 0 then results += sequenceConverter.start()

    executeActions(config.target, config.afterActions)
    results.foreach { result =>
      SqlplusLogger.info(
        s"${result.stage}: ${result.succeeded}/${result.total} succeeded, " +
          s"${result.partials.size} partial, ${result.skipped} skipped, ${result.failures.size} failed")
      result.partials.foreach { partial =>
        val progress = (partial.transferredRows, partial.expectedRows) match {
          case (Some(transferred), Some(expected)) => s" after $transferred/$expected rows"
          case _ => ""
        }
        SqlplusLogger.warn(s"${result.stage} ${partial.item}$progress: ${partial.message}")
      }
      result.failures.foreach { failure =>
        val progress = (failure.transferredRows, failure.expectedRows) match {
          case (Some(transferred), Some(expected)) => s" after $transferred/$expected rows"
          case _ => ""
        }
        SqlplusLogger.warn(s"${result.stage} ${failure.item}$progress: ${failure.message}")
      }
    }
    SqlplusLogger.info(s"transport complete using ${sw}")
    results.forall(_.isSuccess)
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
              SqlplusLogger.info("execute sql scripts")
              SqlAction.execute(source.dataSource, sqls)
            case None =>
              if (acf.properties.contains("file")) {
                val f = new File(acf.properties("file"))
                require(f.exists(), "sql file:" + f.getAbsolutePath + " doesn't exists")
                SqlplusLogger.info("execute sql scripts " + f.getAbsolutePath)
                SqlAction.execute(source.dataSource, f)
              }
        case _ => SqlplusLogger.warn("Cannot support " + acf.category)
      }
    }
  }

  private def filterTables(cfg: TableConfig, srcSchema: Schema, targetSchema: Schema): List[(Table, Table, Option[String])] = {
    val filter = new NameFilter()
    for (include <- cfg.includes) filter.include(include)
    for (exclude <- cfg.excludes) filter.exclude(exclude)

    val tables = srcSchema.filterTables(cfg.includes, cfg.excludes)
    val tablePairs = Collections.newMap[String, (Table, Table, Option[String])]

    for (src <- tables) {
      val tar = src.clone()
      tar.updateSchema(targetSchema)
      cfg.toCase foreach {
        case "lower" => tar.toCase(true)
        case "upper" => tar.toCase(false)
      }
      cfg.prefix foreach { p =>
        tar.name = Identifier(p + tar.name.value, tar.name.quoted)
      }
      if (cfg.useUnloggedTable) {
        tar.tableType = TableType.Unlogged
      }
      tar.attach(targetSchema.database.engine)
      tablePairs.put(tar.name.toString, (src, tar, cfg.getWhere(src)))
    }
    tablePairs.values.toList
  }

  private def filterViews(cfg: ViewConfig, srcSchema: Schema, targetSchema: Schema): List[(View, Table, Option[String])] = {
    if (null == cfg) return List.empty
    val views = srcSchema.filterViews(cfg.includes, cfg.excludes)
    val tablePairs = Collections.newMap[String, (View, Table, Option[String])]

    for (src <- views) {
      val tar = src.toTable
      tar.updateSchema(targetSchema)
      cfg.toCase foreach {
        case "lower" => tar.toCase(true)
        case "upper" => tar.toCase(false)
      }
      tar.attach(targetSchema.database.engine)
      tablePairs.put(tar.name.toString, (src, tar, cfg.getWhere(src)))
    }
    tablePairs.values.toList
  }
}
