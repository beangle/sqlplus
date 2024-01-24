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
import org.beangle.data.jdbc.meta.Schema.NameFilter
import org.beangle.data.jdbc.meta.{Identifier, Relation}
import org.beangle.db.transport.Config.Source

import javax.sql.DataSource
import scala.xml.Node

object Config {

  def apply(xml: scala.xml.Elem): Config = {
    val threads = Config.maxtheads(xml)
    val source = Config.db(xml, "source", threads)
    val target = Config.db(xml, "target", threads)
    val tasks = Config.tasks(xml, source, target)
    val config = new Config(source, target, tasks, threads,
      Config.bulkSize(xml), Config.datarange(xml))

    config.beforeActions = beforeAction(xml)
    config.afterActions = afterAction(xml)
    config
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
    val defaultBulkSize = 50000
    Numbers.toInt(bs, defaultBulkSize)
  }

  private def attr(n: Node, name: String): String = {
    (n \ s"@${name}").text.trim()
  }

  private def lowcaseAttr(n: Node, name: String): String = {
    (n \ s"@${name}").text.toLowerCase.trim()
  }

  private def tasks(xml: scala.xml.Elem, source: Config.Source, target: Config.Source): Seq[Task] = {
    val tasks = Collections.newBuffer[Task]
    var id = 0
    (xml \\ "task") foreach { ele =>
      val task = new Task(id, source, target)
      id += 1
      val from = source.parse(attr(ele, "from"))
      val to = target.parse(attr(ele, "to"))

      require(Strings.isNotBlank(from._2.value), "task need from schema property")
      require(Strings.isNotBlank(to._2.value), "task need from schema property")

      task.path(from, to)
      val tableConfig = new TableConfig
      (ele \\ "tables" \ "@lowercase") foreach { e =>
        if (e.text == "true") tableConfig.lowercase = Some(true)
      }
      tableConfig.withIndex = "true" == (xml \\ "tables" \ "@index").text
      tableConfig.withConstraint = "true" == (xml \\ "tables" \ "@constraint").text
      tableConfig.includes = (xml \\ "tables" \\ "includes") flatten (e => Strings.split(e.text.trim.toLowerCase()))
      tableConfig.excludes = (xml \\ "tables" \\ "excludes") flatten (e => Strings.split(e.text.trim.toLowerCase()))
      tableConfig.wheres = (xml \\ "tables" \\ "where").map(e => lowcaseAttr(e, "table") -> attr(e, "value")).toMap
      task.table = tableConfig

      val viewConfig = new ViewConfig
      (ele \\ "views" \ "@lowercase") foreach { e =>
        if (e.text == "true") viewConfig.lowercase = Some(true)
      }
      viewConfig.includes = (xml \\ "views" \\ "includes") flatten (e => Strings.split(e.text.trim.toLowerCase()))
      viewConfig.excludes = (xml \\ "views" \\ "excludes") flatten (e => Strings.split(e.text.trim.toLowerCase()))
      viewConfig.wheres = (xml \\ "views" \\ "where").map(e => lowcaseAttr(e, "table") -> attr(e, "value")).toMap
      task.view = viewConfig

      val seqConfig = new SeqConfig
      seqConfig.includes = Strings.split((xml \\ "sequences" \\ "includes").text.trim).toSeq
      seqConfig.excludes = Strings.split((xml \\ "sequences" \\ "excludes").text.trim).toSeq
      task.sequence = seqConfig
      tasks.addOne(task)
    }
    tasks.toList
  }

  private def db(xml: scala.xml.Elem, target: String, threads: Int): Source = {
    val dbconf = DataSourceUtils.parseXml((xml \\ target).head)
    val maximumPoolSize = dbconf.props.getOrElse("maximumPoolSize", "1").toInt
    if (maximumPoolSize <= threads) {
      dbconf.props.put("maximumPoolSize", (threads + 1).toString)
    }
    val ds = DataSourceFactory.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props)
    new Source(Engines.forDataSource(ds), ds)
  }

  private def beforeAction(xml: scala.xml.Elem): Iterable[ActionConfig] = {
    (xml \\ "actions" \\ "before" \\ "sql").map { x =>
      val contents = if (Strings.isBlank(x.text)) None else Some(x.text.trim())
      ActionConfig("script", contents, Map("file" -> (x \ "@file").text))
    }
  }

  private def afterAction(xml: scala.xml.Elem): Iterable[ActionConfig] = {
    (xml \\ "actions" \\ "after" \\ "sql").map { x =>
      val contents = if (Strings.isBlank(x.text)) None else Some(x.text.trim())
      ActionConfig("script", contents, Map("file" -> (x \ "@file").text))
    }
  }

  abstract class DataflowConfig {
    var includes: Seq[String] = _
    var excludes: Seq[String] = _
    var lowercase: Option[Boolean] = None
    var wheres: Map[String, String] = Map.empty

    def buildNameFilter(): NameFilter = {
      val filter = new NameFilter()
      for (include <- includes) filter.include(include)
      for (exclude <- excludes) filter.exclude(exclude)
      filter
    }

    def getWhere(r: Relation): Option[String] = {
      wheres.get(r.name.value.toLowerCase)
    }
  }

  final class TableConfig extends DataflowConfig {
    var withIndex: Boolean = true
    var withConstraint: Boolean = true
  }

  final class ViewConfig extends DataflowConfig {
  }

  final class SeqConfig {
    var includes: Seq[String] = _
    var excludes: Seq[String] = _
  }

  class Source(val engine: Engine, val dataSource: DataSource) {
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
  }

  class Task(var id: Int, val source: Config.Source, val target: Config.Source) {
    var table: TableConfig = _
    var view: ViewConfig = _
    var sequence: SeqConfig = _

    def path(from: (Option[Identifier], Identifier), to: (Option[Identifier], Identifier)): Unit = {
      this.fromCatalog = from._1
      this.fromSchema = from._2

      this.toCatalog = to._1
      this.toSchema = to._2
    }

    var fromSchema: Identifier = _
    var fromCatalog: Option[Identifier] = None
    var toSchema: Identifier = _
    var toCatalog: Option[Identifier] = None

    override def equals(obj: Any): Boolean = {
      obj match
        case o: Task => o.id == this.id
        case _ => false
    }

    override def hashCode(): Int = id
  }
}

class Config(val source: Config.Source, val target: Config.Source,
             val tasks: Seq[Config.Task], val maxthreads: Int,
             val bulkSize: Int,
             val dataRange: (Int, Int)) {

  var beforeActions: Iterable[ActionConfig] = _
  var afterActions: Iterable[ActionConfig] = _
}
