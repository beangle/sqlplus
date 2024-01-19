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

case class ActionConfig(category: String, properties: Map[String, String])

class SqlAction(val dataSource: DataSource, fileName: String) extends Action with Logging {

  require(new File(fileName).exists(), "sql file:" + fileName + " doesnot exists")

  def process(): Boolean = {
    val executor = new JdbcExecutor(dataSource)
    executeFile(executor)
  }

  def executeFile(executor: JdbcExecutor): Boolean = {
    logger.info("execute sql scripts " + fileName)
    readSql(fileName) foreach { s =>
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

  def readSql(name: String): Seq[String] = {
    val content = IOs.readString(new FileInputStream(new File(name)))
    val statements = Strings.split(content, ";")
    statements.map(x => x.replace('\r', '\n').trim).toList
  }
}
