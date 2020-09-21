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
package org.beangle.db.transport

import java.io.FileInputStream

import org.beangle.commons.collection.Collections
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.ds.DataSourceUtils
import org.beangle.data.jdbc.meta.{Constraint, Table}
import org.beangle.db.transport.Config.Source
import org.beangle.db.transport.schema._

object Reactor extends Logging {

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage: Reactor /path/to/your/conversion.xml");
      return
    }
    val xml = scala.xml.XML.load(new FileInputStream(args(0)))
    new Reactor(Config(xml)).start()
  }
}

class Reactor(val config: Config) {
  var sourceWrapper: SchemaWrapper = config.source.buildWrapper()
  var targetWrapper: SchemaWrapper = config.target.buildWrapper()

  def start() = {
    val loadextra = config.source.table.withIndex || config.source.table.withConstraint
    sourceWrapper.loadMetas(loadextra, true)
    targetWrapper.loadMetas(loadextra, true)

    val converters = new collection.mutable.ListBuffer[Converter]

    val dataConverter = new TableConverter(sourceWrapper, targetWrapper, config.maxthreads,
      config.bulkSize, config.dataRange, config.conversionModel)
    val tables = filterTables(config.source, sourceWrapper, targetWrapper);
    dataConverter.addAll(tables)

    converters += dataConverter
    if (config.source.table.withIndex) {
      val indexConverter = new IndexConverter(sourceWrapper, targetWrapper)
      indexConverter.tables ++= tables.map(_._2)
      converters += indexConverter
    }

    if (config.source.table.withConstraint) {
      val contraintConverter = new ConstraintConverter(sourceWrapper, targetWrapper)
      contraintConverter.addAll(filterConstraints(tables))
      converters += contraintConverter
    }

    val sequenceConverter = new SequenceConverter(sourceWrapper, targetWrapper)
    val sequences = sourceWrapper.schema.filterSequences(config.source.sequence.includes, config.source.sequence.excludes)
    sequences foreach { n =>
      n.schema = config.target.getSchema
      n.toCase(config.source.sequence.lowercase)
      n.attach(config.target.engine)
    }
    sequenceConverter.addAll(sequences)
    converters += sequenceConverter

    for (converter <- converters)
      converter.start()

    //cleanup
    DataSourceUtils.close(config.source.dataSource)
    DataSourceUtils.close(config.target.dataSource)
  }

  private def filterTables(source: Source, srcWrapper: SchemaWrapper, targetWrapper: SchemaWrapper): List[Tuple2[Table, Table]] = {
    val tables = sourceWrapper.schema.filterTables(config.source.table.includes, config.source.table.excludes)
    val tablePairs = Collections.newMap[String, Tuple2[Table, Table]]

    for (srcTable <- tables) {
      val targetTable = srcTable.clone()
      targetTable.updateSchema(targetWrapper.schema)
      if (source.table.lowercase) {
        targetTable.toCase(true)
      }
      targetTable.attach(targetWrapper.engine)
      tablePairs.put(targetTable.name.toString, (srcTable -> targetTable))
    }
    tablePairs.values.toList
  }

  private def filterConstraints(tables: List[Tuple2[Table, Table]]): List[Constraint] = {
    val contraints = new collection.mutable.ListBuffer[Constraint]
    for (table <- tables)
      contraints ++= table._2.foreignKeys
    contraints.toList
  }

}
