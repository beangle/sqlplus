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
import org.beangle.commons.io.Files
import org.beangle.commons.lang.{Numbers, Strings}
import org.beangle.jdbc.ds.{DataSourceUtils, Source}
import org.beangle.jdbc.meta.Schema.NameFilter
import org.beangle.jdbc.meta.{Identifier, Relation}

import java.io.InputStream
import scala.collection.immutable.Seq
import scala.xml.{Node, NodeSeq}

object Config {

  private val defaultBulkSize = 50000

  def apply(workdir: String, is: InputStream): Config = {
    val xml = scala.xml.XML.load(is)
    val threads = Config.maxtheads(xml)
    val source = Config.db(xml, "source", threads)
    val target = Config.db(xml, "target", threads)
    val tasks = Config.tasks(xml, source, target)
    val config = new Config(source, target, tasks, threads,
      Config.bulkSize(xml), Config.datarange(xml))

    config.beforeActions = actions(workdir, xml \\ "actions" \ "before" \ "sql")
    config.afterActions = actions(workdir, xml \\ "actions" \ "after" \ "sql")
    config
  }

  def apply(source: Source, target: Source,
            tasks: collection.Seq[Config.Task]): Config = {
    new Config(source, target, tasks, Runtime.getRuntime.availableProcessors(), defaultBulkSize, (1, 10000000))
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
    Numbers.toInt(bs, defaultBulkSize)
  }

  private def attr(n: Node, name: String): String = {
    (n \ s"@${name}").text.trim()
  }

  private def lowcaseAttr(n: Node, name: String): String = {
    (n \ s"@${name}").text.toLowerCase.trim()
  }

  private def tasks(xml: scala.xml.Elem, source: Source, target: Source): Seq[Task] = {
    val tasks = Collections.newBuffer[Task]
    (xml \\ "task") foreach { ele =>
      val task = new Task(source, target)
      val from = source.parse(attr(ele, "from"))
      val to = target.parse(attr(ele, "to"))

      require(Strings.isNotBlank(from._2.value), "task need from schema property")
      require(Strings.isNotBlank(to._2.value), "task need to schema property")

      task.path(from, to)
      val tableConfig = new TableConfig
      (ele \ "tables" \ "@lowercase") foreach { e =>
        if (e.text == "true") tableConfig.lowercase = Some(true)
      }
      tableConfig.withIndex = "true" == (ele \ "tables" \ "@index").text
      tableConfig.withConstraint = "true" == (ele \ "tables" \ "@constraint").text
      tableConfig.includes = (ele \ "tables" \ "includes") flatten (e => Strings.split(e.text.trim.toLowerCase()))
      tableConfig.excludes = (ele \ "tables" \ "excludes") flatten (e => Strings.split(e.text.trim.toLowerCase()))
      tableConfig.wheres = (ele \ "tables" \ "where").map(e => lowcaseAttr(e, "table") -> attr(e, "value")).toMap
      task.table = tableConfig

      val viewConfig = new ViewConfig
      (ele \ "views" \ "@lowercase") foreach { e =>
        if (e.text == "true") viewConfig.lowercase = Some(true)
      }
      viewConfig.includes = (ele \ "views" \ "includes") flatten (e => Strings.split(e.text.trim.toLowerCase()))
      viewConfig.excludes = (ele \ "views" \ "excludes") flatten (e => Strings.split(e.text.trim.toLowerCase()))
      viewConfig.wheres = (ele \ "views" \ "where").map(e => lowcaseAttr(e, "table") -> attr(e, "value")).toMap
      task.view = viewConfig

      val seqConfig = new SeqConfig
      seqConfig.includes = Strings.split((ele \ "sequences" \ "includes").text.trim).toSeq
      seqConfig.excludes = Strings.split((ele \ "sequences" \ "excludes").text.trim).toSeq
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
    Source(dbconf)
  }

  private def actions(workdir: String, nodes: NodeSeq): Iterable[ActionConfig] = {
    nodes.flatMap { x =>
      val contents = if (Strings.isBlank(x.text)) None else Some(x.text.trim())
      if (contents.isEmpty) {
        var filePath = (x \ "@file").text
        if (Strings.isNotBlank(filePath)) {
          filePath = Files.forName(workdir, filePath).getAbsolutePath
          Some(ActionConfig("script", contents, Map("file" -> filePath)))
        } else None
      } else {
        Some(ActionConfig("script", contents, Map.empty))
      }
    }
  }

  abstract class DataflowConfig {
    var includes: Seq[String] = List.empty
    var excludes: Seq[String] = List.empty
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

  object TableConfig {
    def all: TableConfig = {
      val cfg = new TableConfig
      cfg.includes = Seq("*")
      cfg
    }

    def none: TableConfig = {
      val cfg = new TableConfig
      cfg.excludes = Seq("*")
      cfg
    }
  }

  object ViewConfig {
    def all: ViewConfig = {
      val cfg = new ViewConfig
      cfg.includes = Seq("*")
      cfg
    }

    def none: ViewConfig = {
      val cfg = new ViewConfig
      cfg.excludes = Seq("*")
      cfg
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

  class Task(val source: Source, val target: Source) {
    var table: TableConfig = _
    var view: ViewConfig = _
    var sequence: SeqConfig = _

    def path(from: (Option[Identifier], Identifier), to: (Option[Identifier], Identifier)): this.type = {
      this.fromCatalog = from._1
      this.fromSchema = from._2

      this.toCatalog = to._1
      this.toSchema = to._2
      this
    }

    var fromSchema: Identifier = _
    var fromCatalog: Option[Identifier] = None
    var toSchema: Identifier = _
    var toCatalog: Option[Identifier] = None
  }
}

class Config(val source: Source, val target: Source,
             val tasks: collection.Seq[Config.Task], val maxthreads: Int,
             val bulkSize: Int,
             val dataRange: (Int, Int)) {

  var beforeActions: Iterable[ActionConfig] = List.empty
  var afterActions: Iterable[ActionConfig] = List.empty
}
