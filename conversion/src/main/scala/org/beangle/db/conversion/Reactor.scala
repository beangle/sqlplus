/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.db.conversion

import java.io.FileInputStream
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.meta.Constraint
import org.beangle.data.jdbc.meta.Table
import Config.Source
import org.beangle.db.conversion.converter.SequenceConverter
import org.beangle.db.conversion.converter.ConstraintConverter
import org.beangle.db.conversion.converter.IndexConverter
import org.beangle.db.conversion.converter.TableConverter
import org.beangle.data.jdbc.ds.DataSourceUtils

object Reactor extends Logging {

  def main(args: Array[String]) {
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

    val dataConverter = new TableConverter(sourceWrapper, targetWrapper, config.maxthreads)
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
      n.schema = config.target.schema
      n.toCase(config.source.sequence.lowercase)
      n.attach(config.target.dialect)
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
    val tablePairs = new collection.mutable.ListBuffer[Tuple2[Table, Table]]
    for (srcTable <- tables) {
      var targetTable = srcTable.clone()
      targetTable.updateSchema(targetWrapper.schema.name)
      targetTable.toCase(source.table.lowercase)
      targetTable.attach(targetWrapper.dialect)
      tablePairs += (srcTable -> targetTable)
    }
    tablePairs.toList
  }

  private def filterConstraints(tables: List[Tuple2[Table, Table]]): List[Constraint] = {
    val contraints = new collection.mutable.ListBuffer[Constraint]
    for (table <- tables)
      contraints ++= table._2.foreignKeys
    contraints.toList
  }

}
