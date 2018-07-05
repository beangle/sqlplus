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
package org.beangle.db.conversion

import org.beangle.commons.lang.{ Numbers, Strings }
import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.data.jdbc.ds.{ DataSourceUtils, DatasourceConfig }
import org.beangle.data.jdbc.meta.{ Database, Identifier, Schema }
import org.beangle.db.conversion.schema.SchemaWrapper

import javax.sql.DataSource

object Config {

  def apply(xml: scala.xml.Elem): Config = {
    new Config(Config.source(xml), Config.target(xml), Config.maxtheads(xml), Config.bulkSize(xml), Config.model(xml))
  }

  private def model(xml: scala.xml.Elem): ConversionModel.Value = {
    val mt = (xml \ "@model").text.trim
    if (Strings.isEmpty(mt)) ConversionModel.Recreate
    else ConversionModel.withName(mt)
  }

  private def maxtheads(xml: scala.xml.Elem): Int = {
    val mt = (xml \ "@maxthreads").text.trim
    val maxthreads = Numbers.toInt(mt, 5)
    if (maxthreads > 0) maxthreads else 5
  }

  private def bulkSize(xml: scala.xml.Elem): Int = {
    val bs = (xml \ "@bulksize").text.trim
    val bsv = Numbers.toInt(bs, 30000)
    if (bsv > 10000) bsv else 30000
  }

  private def source(xml: scala.xml.Elem): Source = {
    val dbconf = DatasourceConfig.build((xml \\ "source").head)

    val ds = DataSourceUtils.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props)
    val source = new Source(dbconf.dialect, ds)
    source.schema = dbconf.schema
    source.catalog = dbconf.catalog
    val tableConfig = new TableConfig
    tableConfig.lowercase = "true" == (xml \\ "tables" \ "@lowercase").text
    tableConfig.withIndex = "false" != (xml \\ "tables" \ "@index").text
    tableConfig.withConstraint = "false" != (xml \\ "tables" \ "@constraint").text
    tableConfig.includes = Strings.split((xml \\ "tables" \\ "includes").text.trim)
    tableConfig.excludes = Strings.split((xml \\ "tables" \\ "excludes").text.trim)
    source.table = tableConfig

    val seqConfig = new SeqConfig
    seqConfig.includes = Strings.split((xml \\ "sequences" \\ "includes").text.trim)
    seqConfig.excludes = Strings.split((xml \\ "sequences" \\ "excludes").text.trim)
    seqConfig.lowercase = "true" == (xml \\ "sequences" \ "@lowercase").text
    source.sequence = seqConfig

    source
  }

  private def target(xml: scala.xml.Elem): Target = {
    val dbconf = DatasourceConfig.build((xml \\ "target").head)

    val target = new Target(dbconf.dialect, DataSourceUtils.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props))
    target.schema = dbconf.schema
    target.catalog = dbconf.catalog
    target
  }

  final class TableConfig {
    var includes: Seq[String] = _
    var excludes: Seq[String] = _
    var lowercase: Boolean = true
    var withIndex: Boolean = true
    var withConstraint: Boolean = true
  }

  final class SeqConfig {
    var includes: Seq[String] = _
    var excludes: Seq[String] = _
    var lowercase: Boolean = false
  }

  class SchemaHolder(val dialect: Dialect, val dataSource: DataSource) {
    val database = new Database(dialect.engine)
    var schema: Identifier = _
    var catalog: Identifier = _

    def getSchema: Schema = {
      if (null == schema) schema = Identifier(dialect.defaultSchema)
      val rs = database.getOrCreateSchema(schema)
      rs.catalog = Option(catalog)
      rs
    }
  }

  final class Source(dialect: Dialect, dataSource: DataSource) extends SchemaHolder(dialect, dataSource) {
    var table: TableConfig = _
    var sequence: SeqConfig = _

    def buildWrapper(): SchemaWrapper = {
      new SchemaWrapper(dataSource, dialect, getSchema)
    }
  }

  final class Target(dialect: Dialect, dataSource: DataSource) extends SchemaHolder(dialect, dataSource) {
    def buildWrapper(): SchemaWrapper = {
      new SchemaWrapper(dataSource, dialect, getSchema)
    }
  }
}

class Config(val source: Config.Source, val target: Config.Target, val maxthreads: Int, val bulkSize: Int, val conversionModel: ConversionModel.Value) {
}

object ConversionModel extends Enumeration(1) {
  val Recreate, CompareCount = Value
}