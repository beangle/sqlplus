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
import org.jline.keymap.KeyMap
import org.jline.reader.impl.history.DefaultHistory
import org.jline.reader.{LineReader, LineReaderBuilder, Reference}
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp.Capability

import java.nio.file.Paths

/** JLine-based interactive shell with command history. */
class LineShell(appName: String = "sqlplus",
                meta: ShellCompleter.Meta = ShellCompleter.Meta.empty) extends AutoCloseable {

  // dumb(true): IDEA/sbt console is not a real TTY; fall back instead of failing
  // JLine 4: use JNI terminal provider (jansi removed); Windows 10+ uses native console/ANSI
  private val terminal = TerminalBuilder.builder()
    .system(true)
    .dumb(true)
    .build()

  private val reader = LineReaderBuilder.builder()
    .terminal(terminal)
    .appName(appName)
    .parser(new SqlStatementParser)
    .completer(new ShellCompleter(meta))
    .option(LineReader.Option.CASE_INSENSITIVE, true)
    .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
    .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), s".$appName" + "_history"))
    .variable(LineReader.SECONDARY_PROMPT_PATTERN, "   -> ")
    // Avoid "Display all N possibilities?" for our capped (~100) metadata lists
    .variable(LineReader.LIST_MAX, Integer.valueOf(200))
    .build()

  // JLine enables application keypad mode but does not bind the terminal's
  // key_enter capability (for example, PuTTY may send Enter as ESC O M).
  private val acceptLine = new Reference(LineReader.ACCEPT_LINE)
  private val enterKeys = Seq("\r", "\n", KeyMap.key(terminal, Capability.key_enter)).filter(_ != null).distinct
  reader.getKeyMaps.get(LineReader.MAIN).bind(acceptLine, enterKeys*)

  /** Reads a line (or a finished multi-line SQL statement via SqlStatementParser). */
  def readLine(prompt: String): String = {
    try reader.readLine(prompt)
    catch
      case _: org.jline.reader.UserInterruptException => ""
      case _: org.jline.reader.EndOfFileException => null
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
