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
import org.beangle.commons.lang.{Numbers, Strings}
import org.beangle.data.jdbc.ds.{DataSourceFactory, DataSourceUtils}
import org.beangle.data.jdbc.engine.{Engine, Engines}
import org.beangle.data.jdbc.meta.{Database, Identifier, Schema}
import org.beangle.db.transport.Config.Source
import org.beangle.db.transport.schema.SchemaWrapper

import javax.sql.DataSource

object Config {

  def apply(xml: scala.xml.Elem): Config = {
    val source = Config.db(xml, "source")
    val target = Config.db(xml, "target")
    val tasks = Config.tasks(xml, source, target)
    val config = new Config(source, target, tasks, Config.maxtheads(xml),
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

  private def tasks(xml: scala.xml.Elem, source: Config.Source, target: Config.Source): Seq[Task] = {
    val tasks = Collections.newBuffer[Task]
    (xml \\ "task") foreach { ele =>
      val task = new Task(source, target)
      val from = source.parse((ele \ "@from").text)
      val to = target.parse((ele \ "@to").text)
      require(Strings.isNotBlank(from._2.value), "task need from schema property")
      require(Strings.isNotBlank(to._2.value), "task need from schema property")

      task.path(from, to)
      val tableConfig = new TableConfig
      (ele \\ "tables" \ "@lowercase") foreach { e =>
        if (e.text == "true") tableConfig.lowercase = Some(true)
      }
      tableConfig.withIndex = "false" != (xml \\ "tables" \ "@index").text
      tableConfig.withConstraint = "false" != (xml \\ "tables" \ "@constraint").text
      tableConfig.includes = (xml \\ "tables" \\ "includes") flatten (e => Strings.split(e.text.trim.toLowerCase()))
      tableConfig.excludes = (xml \\ "tables" \\ "excludes") flatten (e => Strings.split(e.text.trim.toLowerCase()))
      task.table = tableConfig

      val seqConfig = new SeqConfig
      seqConfig.includes = Strings.split((xml \\ "sequences" \\ "includes").text.trim).toSeq
      seqConfig.excludes = Strings.split((xml \\ "sequences" \\ "excludes").text.trim).toSeq
      task.sequence = seqConfig
      tasks.addOne(task)
    }
    tasks.toList
  }

  private def db(xml: scala.xml.Elem, target: String): Source = {
    val dbconf = DataSourceUtils.parseXml((xml \\ target).head)

    val ds = DataSourceFactory.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props)
    new Source(Engines.forDataSource(ds), ds)
  }

  private def beforeAction(xml: scala.xml.Elem): Iterable[ActionConfig] = {
    (xml \\ "actions" \\ "before" \\ "sql").map { x =>
      ActionConfig("script", Map("file" -> (x \ "@file").text))
    }
  }

  private def afterAction(xml: scala.xml.Elem): Iterable[ActionConfig] = {
    (xml \\ "actions" \\ "after" \\ "sql").map { x =>
      ActionConfig("script", Map("file" -> (x \ "@file").text))
    }
  }

  final class TableConfig {
    var includes: Seq[String] = _
    var excludes: Seq[String] = _
    var lowercase: Option[Boolean] = None
    var withIndex: Boolean = true
    var withConstraint: Boolean = true
  }

  final class SeqConfig {
    var includes: Seq[String] = _
    var excludes: Seq[String] = _
  }

  class Source(val engine: Engine, val dataSource: DataSource) {
    val database = new Database(engine)

    def parse(schemaName: String): (Option[Identifier], Identifier) = {
      if (schemaName.isBlank) {
        (None, engine.toIdentifier(engine.defaultSchema))
      } else if (schemaName.contains(".")) {
        val c = Strings.substringBefore(schemaName, ".")
        val s = Strings.substringBefore(schemaName, ".")
        (Option(engine.toIdentifier(c)), engine.toIdentifier(s))
      } else {
        (None, engine.toIdentifier(schemaName))
      }
    }

    def getSchema(catalog: Option[Identifier], schema: Identifier): Schema = {
      val s =
        if (null == schema) engine.toIdentifier(engine.defaultSchema)
        else engine.toIdentifier(schema.value)
      val rs = database.getOrCreateSchema(s)
      rs.catalog = catalog
      rs
    }

    def buildWrapper(catalog: Option[Identifier], schema: Identifier): SchemaWrapper = {
      new SchemaWrapper(dataSource, engine, getSchema(catalog, schema))
    }
  }

  class Task(val source: Config.Source, val target: Config.Source) {
    var table: TableConfig = _
    var sequence: SeqConfig = _

    def path(from: (Option[Identifier], Identifier), to: (Option[Identifier], Identifier)): Unit = {
      this.fromCatalog = from._1
      this.fromSchema = from._2

      this.toCatalog = to._1
      this.toSchema = to._2
    }

    def sourceWrapper(): SchemaWrapper = {
      source.buildWrapper(fromCatalog, fromSchema)
    }

    def targetWrapper(): SchemaWrapper = {
      target.buildWrapper(toCatalog, toSchema)
    }

    var fromSchema: Identifier = _
    var fromCatalog: Option[Identifier] = None
    var toSchema: Identifier = _
    var toCatalog: Option[Identifier] = None
  }

}


class Config(val source: Config.Source, val target: Config.Source,
             val tasks: Seq[Config.Task], val maxthreads: Int,
             val bulkSize: Int,
             val dataRange: (Int, Int),
             val conversionModel: ConversionModel.Value) {

  var beforeActions: Iterable[ActionConfig] = _
  var afterActions: Iterable[ActionConfig] = _
}

object ConversionModel extends Enumeration(1) {
  val Recreate, CompareCount = Value
}
