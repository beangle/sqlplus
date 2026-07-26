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

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.jdbc.query.JdbcExecutor
import org.beangle.sqlplus.SqlplusLogger

import java.io.{File, FileInputStream}
import javax.sql.DataSource

trait Action {
  def process(): Boolean
}

case class ActionConfig(category: String, contents: Option[String], properties: Map[String, String]) {}

object SqlAction {
  private val LoopOption = raw"([a-zA-Z][\w-]*)=(\d+)".r

  private[transport] case class LoopDirective(batchSize: Int, maxBatches: Int, label: String)

  def readSqls(file: File): Seq[String] = {
    readSqls(IOs.readString(new FileInputStream(file)))
  }

  def readSqls(contents: String): Seq[String] = {
    val statements = Strings.split(contents, ";")
    statements.map(x => x.replace('\r', '\n').trim).toList
  }

  def execute(dataSource: DataSource, contents: String): Boolean = {
    new SqlAction(dataSource, readSqls(contents)).process()
  }

  def execute(dataSource: DataSource, file: File): Boolean = {
    new SqlAction(dataSource, readSqls(file)).process()
  }

  private[transport] def parseLoopDirective(comment: String): Option[LoopDirective] = {
    val content = comment.stripPrefix("--").trim
    if (!(content.equalsIgnoreCase("@loop") || content.toLowerCase.startsWith("@loop "))) return None

    val tokens = Strings.split(content.substring(5).trim, " ").toList
    val options = tokens.takeWhile(_.contains("=")).map {
      case LoopOption(name, value) => name.toLowerCase -> value.toInt
      case token => throw new IllegalArgumentException(s"Invalid loop option: $token")
    }.toMap
    val unknown = options.keySet -- Set("batch-size", "max-batches")
    require(unknown.isEmpty, s"Unknown loop options: ${unknown.mkString(", ")}")

    val batchSize = options.getOrElse("batch-size", 100000)
    val maxBatches = options.getOrElse("max-batches", 50)
    require(batchSize > 0, "batch-size must be greater than zero")
    require(maxBatches > 0, "max-batches must be greater than zero")
    val label = tokens.drop(options.size).mkString(" ")
    Some(LoopDirective(batchSize, maxBatches, label))
  }
}

class SqlAction(val dataSource: DataSource, sqls: Seq[String], passthrough: Boolean = true) extends Action {
  val executor = new JdbcExecutor(dataSource)

  def process(): Boolean = {
    var success = true
    sqls foreach { s =>
      if (s.startsWith("--")) {
        var comment = Strings.substringBefore(s, "\n")
        var statement = Strings.substringAfter(s, "\n").trim()
        statement = Strings.replace(statement, "\n", " ")
        SqlAction.parseLoopDirective(comment) match {
          case Some(directive) =>
            if (!executeLoop(statement, directive)) success = false
          case None =>
            comment = Strings.replace(comment, "--", "").trim()
            val sw = new Stopwatch(true)
            executeSql(statement) match {
              case Some(rs) => SqlplusLogger.info(comment + s" ${rs}, using ${sw}")
              case None => success = false
            }
        }
      } else if (Strings.isNotBlank(s)) {
        if (executeSql(s).isEmpty) success = false
      }
    }
    success
  }

  private def executeLoop(sql: String, directive: SqlAction.LoopDirective): Boolean = {
    val lowerSql = sql.toLowerCase.trim
    require(lowerSql.startsWith("insert"), "@loop only supports INSERT statements")
    require(raw"(?s).*\bselect\b.*".r.matches(lowerSql),
      "@loop only supports INSERT ... SELECT statements")
    require(!raw"(?s).*\blimit\b.*".r.matches(lowerSql),
      "@loop adds LIMIT automatically; remove LIMIT from the SQL")
    require(!raw"(?s).*\bon\s+conflict\b.*".r.matches(lowerSql),
      "@loop does not support ON CONFLICT")
    require(!raw"(?s).*\breturning\b.*".r.matches(lowerSql),
      "@loop does not support RETURNING")
    val batchSql = s"$sql limit ${directive.batchSize}"

    val label = if Strings.isBlank(directive.label) then "loop insert" else directive.label
    val totalWatch = new Stopwatch(true)
    var batch = 0
    var total = 0L
    var affected = directive.batchSize
    try {
      while (affected >= directive.batchSize && batch < directive.maxBatches) {
        batch += 1
        val batchWatch = new Stopwatch(true)
        affected = executor.update(batchSql)
        total += affected
        SqlplusLogger.info(
          s"$label batch $batch: $affected, total $total, using $batchWatch")
      }
      if (affected >= directive.batchSize) {
        throw new IllegalStateException(
          s"$label reached max-batches ${directive.maxBatches} after affecting $total rows")
      }
      SqlplusLogger.info(s"$label completed: $total, using $totalWatch")
      true
    } catch {
      case e: Exception =>
        if (!passthrough) throw e
        SqlplusLogger.error(s"$label failed after batch $batch and $total affected rows", e)
        false
    }
  }

  private def executeSql(sql: String): Option[Int] = {
    try {
      if !sql.toLowerCase.trim().startsWith("select") then
        Some(executor.update(sql))
      else Some(0)
    } catch {
      case e: Exception =>
        if (!passthrough) throw e
        else {
          SqlplusLogger.error(s"execute ${sql} failed", e)
          None
        }
    }
  }
}
