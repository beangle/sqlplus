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

import org.beangle.jdbc.script.{Runner, Statement}
import org.beangle.sqlplus.SqlplusLogger

import javax.sql.DataSource

trait Action {
  def process(): Boolean
}

case class ActionConfig(category: String, contents: Option[String], properties: Map[String, String]) {}

class SqlAction(val dataSource: DataSource, statements: Seq[Statement], ignoreError: Boolean = true) extends Action {

  def process(): Boolean = {
    // progress log for leading comments, skip directive lines like `-- @loop ...`
    statements.foreach { s =>
      s.comments.foreach { c =>
        if (!c.trim.startsWith("-- @")) SqlplusLogger.info(c.stripPrefix("--").trim)
      }
    }
    Runner.execute(dataSource, statements, ignoreError = ignoreError)
  }
}
