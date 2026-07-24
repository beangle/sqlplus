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

import org.beangle.commons.lang.Strings
import org.jline.reader.impl.history.DefaultHistory
import org.jline.reader.{LineReader, LineReaderBuilder}
import org.jline.terminal.TerminalBuilder

import java.nio.file.Paths

/** JLine-based interactive shell with command history. */
class LineShell(appName: String = "sqlplus",
                meta: ShellCompleter.Meta = ShellCompleter.Meta.empty) extends AutoCloseable {

  // dumb(true): IDEA/sbt console is not a real TTY; fall back instead of failing
  private val terminal = TerminalBuilder.builder()
    .system(true)
    .jansi(true)
    .dumb(true)
    .build()

  private val reader = LineReaderBuilder.builder()
    .terminal(terminal)
    .appName(appName)
    .completer(new ShellCompleter(meta))
    .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
    .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), s".$appName" + "_history"))
    // Avoid "Display all N possibilities?" for our capped (~100) metadata lists
    .variable(LineReader.LIST_MAX, Integer.valueOf(200))
    .build()

  /** Reads a line with optional history recording (disabled for SQL continuation lines). */
  def readLine(prompt: String, recordHistory: Boolean = true): String = {
    val previous = reader.getVariable(LineReader.DISABLE_HISTORY)
    if !recordHistory then reader.setVariable(LineReader.DISABLE_HISTORY, true)
    try
      try reader.readLine(prompt)
      catch
        case _: org.jline.reader.UserInterruptException => ""
        case _: org.jline.reader.EndOfFileException => null
    finally
      if !recordHistory then
        if previous == null then reader.setVariable(LineReader.DISABLE_HISTORY, false)
        else reader.setVariable(LineReader.DISABLE_HISTORY, previous)
  }

  /** Adds a completed command to history (e.g. a multi-line SQL statement). */
  def addHistory(entry: String): Unit = {
    if Strings.isNotBlank(entry) then reader.getHistory.add(entry.trim)
  }

  /** Prompts for input; returns default when empty. */
  def prompt(msg: String, defaultStr: String = null): String = {
    val promptMsg = msg + (if defaultStr != null then s"(default $defaultStr)" else "")
    val content = readLine(promptMsg)
    if content == null then null
    else if Strings.isEmpty(content) then defaultStr
    else content
  }

  /** Prompts for yes/no confirmation. */
  def confirm(msg: String, yes: Set[String] = Set("Y", "yes"), no: Set[String] = Set("n", "no")): Boolean = {
    var answer = false
    var done = false
    while !done do
      val content = readLine(msg)
      if content == null then done = true
      else
        val trimmed = Strings.trim(content)
        if yes.contains(trimmed) then
          answer = true
          done = true
        else if no.contains(trimmed) then
          answer = false
          done = true
    answer
  }

  override def close(): Unit = {
    reader.getHistory match
      case h: DefaultHistory => h.save()
      case _ =>
    terminal.close()
  }
}
