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
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.ds.DataSourceUtils
import org.beangle.data.jdbc.engine.StoreCase
import org.beangle.data.jdbc.meta.{Constraint, PrimaryKey, Table}
import org.beangle.db.transport.Config.TableConfig
import org.beangle.db.transport.schema.*

import java.io.FileInputStream

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
    config.beforeActions foreach { acf =>
      acf.category match {
        case "script" => new SqlAction(config.source.dataSource, acf.properties("file")).process()
        case _ => logger.warn("Cannot support " + acf.category)
      }
    }

    config.tasks foreach { task =>
      val sourceWrapper = task.sourceWrapper()
      val targetWrapper = task.targetWrapper()

      val loadextra = task.table.withIndex || task.table.withConstraint
      logger.info("loading source metas")
      sourceWrapper.loadMetas(loadextra, true)
      logger.info("loading target metas")
      targetWrapper.loadMetas(loadextra, true)
      targetWrapper.createSchema()

      val converters = new collection.mutable.ListBuffer[Converter]

      val dataConverter = new TableConverter(sourceWrapper, targetWrapper, config.maxthreads,
        config.bulkSize, config.dataRange, config.conversionModel)
      val tables = filterTables(task.table, sourceWrapper, targetWrapper);
      dataConverter.addAll(tables)

      converters += dataConverter

      val pkConverter = new PrimaryKeyConverter(sourceWrapper, targetWrapper)
      pkConverter.addPrimaryKeys(filterPrimaryKeys(tables))
      converters += pkConverter

      if (task.table.withIndex) {
        val indexConverter = new IndexConverter(sourceWrapper, targetWrapper)
        indexConverter.tables ++= tables.map(_._2)
        converters += indexConverter
      }

      if (task.table.withConstraint) {
        val constraintConverter = new ConstraintConverter(sourceWrapper, targetWrapper)
        constraintConverter.addConstraints(filterConstraints(tables))
        converters += constraintConverter
      }

      val sequenceConverter = new SequenceConverter(sourceWrapper, targetWrapper)
      val sequences = sourceWrapper.schema.filterSequences(task.sequence.includes, task.sequence.excludes)
      sequences foreach { n =>
        n.schema = config.target.getSchema(task.toCatalog, task.toSchema)
        if (config.target.engine.storeCase != StoreCase.Mixed) {
          n.toCase(config.target.engine.storeCase == StoreCase.Lower)
        }
        n.attach(config.target.engine)
      }
      sequenceConverter.addAll(sequences)
      converters += sequenceConverter

      for (converter <- converters) {
        converter.start()
      }
    }
    config.afterActions foreach { acf =>
      acf.category match {
        case "script" => new SqlAction(config.target.dataSource, acf.properties("file")).process()
        case _ => logger.warn("Cannot support " + acf.category)
      }
    }
  }

  def close(): Unit = {
    //cleanup
    DataSourceUtils.close(config.source.dataSource)
    DataSourceUtils.close(config.target.dataSource)
  }

  private def filterTables(tableConfig: TableConfig, srcWrapper: SchemaWrapper, targetWrapper: SchemaWrapper): List[Tuple2[Table, Table]] = {
    val tables = srcWrapper.schema.filterTables(tableConfig.includes, tableConfig.excludes)
    val tablePairs = Collections.newMap[String, Tuple2[Table, Table]]

    for (srcTable <- tables) {
      val targetTable = srcTable.clone()
      targetTable.updateSchema(targetWrapper.schema)
      tableConfig.lowercase foreach { lowercase =>
        if (lowercase) targetTable.toCase(true)
      }
      targetTable.attach(targetWrapper.engine)
      tablePairs.put(targetTable.name.toString, srcTable -> targetTable)
    }
    tablePairs.values.toList
  }

  private def filterPrimaryKeys(tables: List[Tuple2[Table, Table]]): List[PrimaryKey] = {
    tables.flatten(_._2.primaryKey)
  }

  private def filterConstraints(tables: List[Tuple2[Table, Table]]): List[Constraint] = {
    tables.flatten(_._2.foreignKeys)
  }

}
