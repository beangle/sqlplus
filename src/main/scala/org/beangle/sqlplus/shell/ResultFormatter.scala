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

import scala.collection.mutable.ArrayBuffer

/** Formats SELECT results in psql-like styles (display-width aware for CJK). */
object ResultFormatter {

  /**
   * psql aligned table. Column widths use terminal display columns (CJK = 2),
   * capped by maxColWidth so long values do not blow up the layout.
   * {{{
   *  id | name
   * ----+-------
   *   1 | alice
   * }}}
   */
  def table(columns: Array[String], rows: Seq[Array[_]], maxColWidth: Int): Seq[String] = {
    if columns.isEmpty then return Seq.empty
    val texts = rows.map(r => columns.indices.map(i => cellText(safeAt(r, i))).toArray)
    val numeric = columns.indices.map { i =>
      texts.nonEmpty && texts.forall(r => isNumericCell(r(i)))
    }.toArray
    val widths = columns.indices.map { i =>
      val headerW = displayWidth(columns(i))
      val dataW = texts.map(r => displayWidth(r(i))).maxOption.getOrElse(0)
      Math.max(1, Math.min(maxColWidth, Math.max(headerW, dataW)))
    }.toArray

    val lines = new ArrayBuffer[String](rows.size + 2)
    lines += alignedRow(columns, widths, Array.fill(columns.length)(false))
    lines += alignedSep(widths)
    texts.foreach(r => lines += alignedRow(r, widths, numeric))
    lines.toSeq
  }

  /**
   * psql expanded (\\x) layout:
   * {{{
   * -[ RECORD 1 ]----
   * id   | 1
   * name | alice
   * }}}
   */
  def vertical(columns: Array[String], rows: Seq[Array[_]]): Seq[String] = {
    if columns.isEmpty then return Seq.empty
    val nameWidth = columns.map(displayWidth).maxOption.getOrElse(0)
    val lines = new ArrayBuffer[String](rows.size * (columns.length + 1))
    rows.zipWithIndex.foreach { (row, idx) =>
      lines += recordBanner(idx + 1)
      columns.indices.foreach { i =>
        lines += s"${padRight(columns(i), nameWidth)} | ${cellText(safeAt(row, i))}"
      }
    }
    lines.toSeq
  }

  def csv(columns: Array[String], rows: Seq[Array[_]]): Seq[String] =
    csvHeader(columns) +: rows.map(r => csvRow(columns, r))

  def csvHeader(columns: Array[String]): String =
    columns.map(escapeCsv).mkString(",")

  def csvRow(columns: Array[String], row: Array[_]): String =
    columns.indices.map(i => escapeCsv(cellText(safeAt(row, i)))).mkString(",")

  private def alignedRow(cells: Array[String], widths: Array[Int], numeric: Array[Boolean]): String = {
    widths.indices.map { i =>
      val raw = if i < cells.length then cells(i) else ""
      val text = truncateToWidth(raw, widths(i))
      if i < numeric.length && numeric(i) then padLeft(text, widths(i))
      else padRight(text, widths(i))
    }.mkString(" | ")
  }

  private def alignedSep(widths: Array[Int]): String =
    widths.map(w => "-" * w).mkString("-+-")

  private def recordBanner(rowNum: Int): String = {
    val label = s"-[ RECORD $rowNum ]"
    label + ("-" * Math.max(4, 20 - label.length))
  }

  /** psql default: null displays as empty. */
  private def cellText(value: Any): String = {
    if value == null then ""
    else
      String.valueOf(value)
        .replace("\r\n", " ")
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
  }

  private def isNumericCell(text: String): Boolean = {
    if text.isEmpty then true
    else
      text.forall(c => c.isDigit || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') &&
        text.exists(_.isDigit)
  }

  /** Terminal display width: full-width / CJK glyphs count as 2. */
  private def displayWidth(text: String): Int = {
    var w = 0
    var i = 0
    while i < text.length do
      val cp = text.codePointAt(i)
      w += (if isWide(cp) then 2 else 1)
      i += Character.charCount(cp)
    w
  }

  private def isWide(cp: Int): Boolean = {
    (cp >= 0x1100 && cp <= 0x115F) ||
      cp == 0x2329 || cp == 0x232A ||
      (cp >= 0x2E80 && cp <= 0xA4CF && cp != 0x303F) ||
      (cp >= 0xAC00 && cp <= 0xD7A3) ||
      (cp >= 0xF900 && cp <= 0xFAFF) ||
      (cp >= 0xFE10 && cp <= 0xFE19) ||
      (cp >= 0xFE30 && cp <= 0xFE6F) ||
      (cp >= 0xFF00 && cp <= 0xFF60) ||
      (cp >= 0xFFE0 && cp <= 0xFFE6) ||
      (cp >= 0x1F300 && cp <= 0x1F64F) ||
      (cp >= 0x1F900 && cp <= 0x1F9FF) ||
      (cp >= 0x20000 && cp <= 0x3FFFD)
  }

  private def truncateToWidth(text: String, max: Int): String = {
    if displayWidth(text) <= max then text
    else if max <= 3 then
      takeWidth(text, max)
    else
      takeWidth(text, max - 3) + "..."
  }

  private def takeWidth(text: String, max: Int): String = {
    val sb = new java.lang.StringBuilder
    var w = 0
    var i = 0
    while i < text.length do
      val cp = text.codePointAt(i)
      val cw = if isWide(cp) then 2 else 1
      if w + cw > max then return sb.toString
      sb.appendCodePoint(cp)
      w += cw
      i += Character.charCount(cp)
    sb.toString
  }

  private def padRight(text: String, width: Int): String = {
    val pad = width - displayWidth(text)
    if pad <= 0 then text else text + " " * pad
  }

  private def padLeft(text: String, width: Int): String = {
    val pad = width - displayWidth(text)
    if pad <= 0 then text else " " * pad + text
  }

  private def safeAt(row: Array[_], i: Int): Any =
    if i < row.length then row(i) else null

  private def escapeCsv(value: String): String = {
    if value == null then ""
    else if value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r") then
      "\"" + value.replace("\"", "\"\"") + "\""
    else value
  }
}
