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

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.query.JdbcExecutor

import java.io.{File, FileInputStream}
import javax.sql.DataSource

trait Action {
  def process(): Boolean
}

case class ActionConfig(category: String, contents: Option[String], properties: Map[String, String]) {}

object SqlAction {
  def readSqls(file: File): Seq[String] = {
    readSqls(IOs.readString(new FileInputStream(file)))
  }

  def readSqls(contents: String): Seq[String] = {
    val statements = Strings.split(contents, ";")
    statements.map(x => x.replace('\r', '\n').trim).toList
  }

  def execute(dataSource: DataSource, contents: String): Unit = {
    new SqlAction(dataSource, readSqls(contents)).process()
  }

  def execute(dataSource: DataSource, file: File): Unit = {
    new SqlAction(dataSource, readSqls(file)).process()
  }
}

class SqlAction(val dataSource: DataSource, sqls: Seq[String]) extends Action with Logging {

  def process(): Boolean = {
    val executor = new JdbcExecutor(dataSource)
    sqls foreach { s =>
      if (s.startsWith("--")) {
        var comment = Strings.substringBefore(s, "\n")
        comment = Strings.replace(comment, "--", "")
        var statement = Strings.substringAfter(s, "\n").trim()
        statement = Strings.replace(statement, "\n", " ")
        val sw = new Stopwatch(true)
        val rs = executor.update(statement)
        logger.info(comment + s" ${rs}, using ${sw}")
      } else if (Strings.isNotBlank(s)) {
        executor.update(s)
      }
    }
    true
  }
}
