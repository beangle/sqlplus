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

import org.beangle.commons.lang.time.Stopwatch
import org.beangle.jdbc.script.{Directive, Runner, Statement}
import org.beangle.sqlplus.SqlplusLogger

import javax.sql.DataSource

trait Action {
  def process(): Boolean
}

case class ActionConfig(category: String, contents: Option[String], properties: Map[String, String]) {}

class SqlAction(val dataSource: DataSource, statements: Seq[Statement], ignoreError: Boolean = true) extends Action {

  def process(): Boolean = {
    Runner.execute(dataSource, statements, ignoreError = ignoreError, onUpdate = { (statement, rows, sw) =>
      val comment = progressComment(statement)
      if comment.nonEmpty && rows >= 0 then
        SqlplusLogger.info(comment + s" ${rows}, using ${sw}")
    })
  }

  /** First leading `--` comment, same label the old SqlAction logged with the update count. */
  private def progressComment(statement: Statement): String = {
    if statement.directive(Directive.Loop).isDefined then ""
    else
      statement.comments
        .map(_.stripPrefix("--").trim)
        .find(c => c.nonEmpty && !c.startsWith("@"))
        .getOrElse("")
  }
}
