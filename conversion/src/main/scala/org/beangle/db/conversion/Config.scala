/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.db.conversion

import org.beangle.commons.lang.{ Numbers, Strings }
import org.beangle.data.jdbc.dialect.{ Dialect, Name }
import org.beangle.data.jdbc.util.{ DatasourceConfig, PoolingDataSourceFactory }
import javax.sql.DataSource

object Config {

  def apply(xml: scala.xml.Elem): Config = {
    new Config(Config.source(xml), Config.target(xml), Config.maxtheads(xml))
  }

  private def maxtheads(xml: scala.xml.Elem): Int = {
    val mt = (xml \ "@maxthreads").text.trim
    val maxthreads = Numbers.toInt(mt, 5)
    if (maxthreads > 0) maxthreads else 5
  }

  private def source(xml: scala.xml.Elem): Source = {
    val dbconf = DatasourceConfig.build((xml \\ "source").head)

    val ds = new PoolingDataSourceFactory(dbconf.driver,
      dbconf.url, dbconf.user, dbconf.password, dbconf.props).getObject
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
    val dbconf = DatasourceConfig.build((xml \\ "target" \\ "db").head)

    val ds = new PoolingDataSourceFactory(dbconf.driver,
      dbconf.url, dbconf.user, dbconf.password, dbconf.props).getObject
    val target = new Target(dbconf.dialect, ds)
    target.schema = dbconf.schema
    target.catalog = dbconf.catalog
    target
  }

  final class TableConfig {
    var includes: Seq[String] = _
    var excludes: Seq[String] = _
    var lowercase: Boolean = false
    var withIndex: Boolean = true
    var withConstraint: Boolean = true
  }

  final class SeqConfig {
    var includes: Seq[String] = _
    var excludes: Seq[String] = _
    var lowercase: Boolean = false

  }

  final class Source(val dialect: Dialect, val dataSource: DataSource) {
    var schema: Name = _
    var catalog: Name = _
    var table: TableConfig = _
    var sequence: SeqConfig = _

    def buildWrapper(): SchemaWrapper = {
      if (null == schema && null != dialect.defaultSchema) schema = Name(dialect.defaultSchema)
      new SchemaWrapper(dataSource, dialect, catalog, schema)
    }

  }

  final class Target(val dialect: Dialect, val dataSource: DataSource) {
    var schema: Name = _
    var catalog: Name = _

    def buildWrapper(): SchemaWrapper = {
      if (null == schema) schema = Name(dialect.defaultSchema)
      new SchemaWrapper(dataSource, dialect, catalog, schema)
    }

  }
}

class Config(val source: Config.Source, val target: Config.Target, val maxthreads: Int) {
}