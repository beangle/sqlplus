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

import org.beangle.commons.lang.{Numbers, Strings}
import org.beangle.data.jdbc.ds.{DataSourceFactory, DataSourceUtils}
import org.beangle.data.jdbc.engine.Engine
import org.beangle.data.jdbc.meta.{Database, Identifier, Schema}
import org.beangle.db.transport.schema.SchemaWrapper

import javax.sql.DataSource

object Config {

  def apply(xml: scala.xml.Elem): Config = {
    val config = new Config(Config.source(xml), Config.target(xml), Config.maxtheads(xml),
      Config.bulkSize(xml), Config.datarange(xml), Config.model(xml))

    config.beforeActions = beforeAction(xml)
    config.afterActions = afterAction(xml)
    config
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

  private def datarange(xml: scala.xml.Elem): Tuple2[Int, Int] = {
    val mt = (xml \ "@datarange").text.trim
    if (Strings.isEmpty(mt)) {
      Tuple2(0, Int.MaxValue)
    } else {
      val min = Strings.trim(Strings.substringBefore(mt, "-"))
      val max = Strings.trim(Strings.substringAfter(mt, "-"))
      Tuple2(Integer.parseInt(min), if (max == "*") Int.MaxValue else Integer.parseInt(max))
    }
  }

  private def bulkSize(xml: scala.xml.Elem): Int = {
    val bs = (xml \ "@bulksize").text.trim
    val bsv = Numbers.toInt(bs, 30000)
    if (bsv > 10000) bsv else 30000
  }

  private def source(xml: scala.xml.Elem): Source = {
    val dbconf = DataSourceUtils.parseXml((xml \\ "source" \\ "db").head)
    val ds = DataSourceFactory.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props)
    val source = new Source(dbconf.engine, ds)
    source.schema = dbconf.schema
    source.catalog = dbconf.catalog
    val tableConfig = new TableConfig
    tableConfig.lowercase = "true" == (xml \\ "tables" \ "@lowercase").text
    tableConfig.withIndex = "false" != (xml \\ "tables" \ "@index").text
    tableConfig.withConstraint = "false" != (xml \\ "tables" \ "@constraint").text
    tableConfig.includes = Strings.split((xml \\ "tables" \\ "includes").text.trim.toLowerCase).toSeq
    tableConfig.excludes = Strings.split((xml \\ "tables" \\ "excludes").text.trim.toLowerCase).toSeq
    source.table = tableConfig

    val seqConfig = new SeqConfig
    seqConfig.includes = Strings.split((xml \\ "sequences" \\ "includes").text.trim).toSeq
    seqConfig.excludes = Strings.split((xml \\ "sequences" \\ "excludes").text.trim).toSeq
    seqConfig.lowercase = "true" == (xml \\ "sequences" \ "@lowercase").text
    source.sequence = seqConfig

    source
  }

  private def target(xml: scala.xml.Elem): Target = {
    val dbconf = DataSourceUtils.parseXml((xml \\ "target" \\ "db").head)

    val target = new Target(dbconf.engine, DataSourceFactory.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props))
    target.schema = dbconf.schema
    target.catalog = dbconf.catalog
    target
  }

  private def beforeAction(xml: scala.xml.Elem): Iterable[ActionConfig] = {
    val actions = (xml \\ "actions" \\ "before" \\ "sql")
    actions.map { x =>
      ActionConfig("script", Map("file" -> (x \ "@file").text))
    }
  }

  private def afterAction(xml: scala.xml.Elem): Iterable[ActionConfig] = {
    val actions = (xml \\ "actions" \\ "after" \\ "sql")
    actions.map { x =>
      ActionConfig("script", Map("file" -> (x \ "@file").text))
    }
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

  class SchemaHolder(val engine: Engine, val dataSource: DataSource) {
    val database = new Database(engine)
    var schema: Identifier = _
    var catalog: Identifier = _

    def getSchema: Schema = {
      if (null == schema) schema = Identifier(engine.defaultSchema)
      val rs = database.getOrCreateSchema(schema)
      rs.catalog = Option(catalog)
      rs
    }
  }

  final class Source(engine: Engine, dataSource: DataSource) extends SchemaHolder(engine, dataSource) {
    var table: TableConfig = _
    var sequence: SeqConfig = _

    def buildWrapper(): SchemaWrapper = {
      new SchemaWrapper(dataSource, engine, getSchema)
    }
  }

  final class Target(engine: Engine, dataSource: DataSource) extends SchemaHolder(engine, dataSource) {
    def buildWrapper(): SchemaWrapper = {
      new SchemaWrapper(dataSource, engine, getSchema)
    }
  }

}

class Config(val source: Config.Source, val target: Config.Target, val maxthreads: Int,
             val bulkSize: Int,
             val dataRange: (Int, Int),
             val conversionModel: ConversionModel.Value) {

  var beforeActions: Iterable[ActionConfig] = _
  var afterActions: Iterable[ActionConfig] = _
}

object ConversionModel extends Enumeration(1) {
  val Recreate, CompareCount = Value
}
