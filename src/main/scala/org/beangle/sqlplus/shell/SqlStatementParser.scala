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

package org.beangle.sqlplus.shell

import org.jline.reader.Parser.ParseContext
import org.jline.reader.impl.DefaultParser
import org.jline.reader.{EOFError, ParsedLine}

/** Continues reading until SQL is terminated (; / \\G), so history stores one entry. */
class SqlStatementParser extends DefaultParser {

  override def parse(line: String, cursor: Int, context: ParseContext): ParsedLine = {
    val parsed = super.parse(line, cursor, context)
    if context == ParseContext.COMPLETE || context == ParseContext.SPLIT_LINE then return parsed

    val trimmed = line.trim
    if trimmed.isEmpty then return parsed
    if SqlStatementParser.hasTerminator(trimmed) then return parsed

    val first = SqlStatementParser.firstSignificantLine(line)
    if SqlStatementParser.isSqlStart(first) then
      throw new EOFError(-1, cursor, "missing terminator", "   -> ")

    parsed
  }
}

object SqlStatementParser {

  def hasTerminator(cmd: String): Boolean =
    cmd.endsWith(";") || cmd.endsWith("/") || cmd.endsWith("\\G") || cmd.endsWith("\\g")

  def terminatorOf(cmd: String): Option[String] = {
    if cmd.endsWith("\\G") || cmd.endsWith("\\g") then Some(cmd.substring(cmd.length - 2))
    else if cmd.endsWith(";") || cmd.endsWith("/") then Some(cmd.substring(cmd.length - 1))
    else None
  }

  def stripTerminator(cmd: String): (String, Boolean) = {
    terminatorOf(cmd.trim) match
      case Some(t) =>
        val body = cmd.trim.substring(0, cmd.trim.length - t.length).trim
        val vertical = t.equalsIgnoreCase("\\G")
        (body, vertical)
      case None => (cmd.trim, false)
  }

  def firstSignificantLine(text: String): String =
    text.linesIterator.map(_.trim).find(_.nonEmpty).getOrElse("").stripLeading

  def isSqlStart(sql: String): Boolean = {
    val cmd = sql.toLowerCase
    cmd.startsWith("select ") || cmd.startsWith("insert ") ||
      cmd.startsWith("alter ") || cmd.startsWith("update ") || cmd.startsWith("delete ") ||
      cmd.startsWith("create ") || cmd.startsWith("drop ") || cmd.startsWith("grant ") ||
      cmd == "select" || cmd == "insert" || cmd == "alter" || cmd == "update" ||
      cmd == "delete" || cmd == "create" || cmd == "drop" || cmd == "grant"
  }
}
