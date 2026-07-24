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

import org.beangle.jdbc.meta.Database
import org.jline.reader.{Candidate, Completer, LineReader, ParsedLine}

import java.util
import scala.jdk.CollectionConverters.*

object ShellCompleter {

  /** Metadata access shared with find/desc (lazy-loaded Database cache). */
  trait Meta {
    /** Schema names for `use` completion. */
    def schemas: Seq[String]

    /** Table/view names (simple + qualified) for find/desc/SQL completion. */
    def relations: Seq[String]
  }

  object Meta {
    val empty: Meta = new Meta {
      def schemas: Seq[String] = Nil

      def relations: Seq[String] = Nil
    }

    def fromDatabase(db: Database): Seq[String] = {
      val engine = db.engine
      db.schemas.values.flatMap { schema =>
        (schema.tables.values ++ schema.views.values).flatMap { r =>
          Seq(r.name.toLiteral(engine), r.qualifiedName)
        }
      }.toSeq.distinct.sorted
    }
  }
}

/** Completer: shell commands, SQL keywords, and metadata names. */
class ShellCompleter(meta: ShellCompleter.Meta = ShellCompleter.Meta.empty) extends Completer {

  /** Cap metadata candidates; remainder is shown as "... (N more)". */
  private val metaMaxCandidates = 100

  private val tableTriggers = Set("find", "desc", "from", "join", "update", "into", "table")

  private val phrases = Seq(
    "help", "info", "exit", "quit",
    "dump schema", "report schema", "validate schema", "dump data",
    "list tmp", "drop tmp", "list schema",
    "find ", "desc ", "use ",
    "set", "set limit ", "set width ", "set format ", "set format table", "set format vertical", "set format csv",
    "spool ", "spool off", "source "
  )

  private val words = Seq(
    "help", "info", "exit", "quit",
    "dump", "report", "validate", "list", "drop", "find", "desc", "use",
    "schema", "data", "tmp",
    "set", "limit", "width", "format", "table", "vertical", "csv",
    "spool", "off", "source",
    "select", "insert", "update", "delete", "alter", "create", "grant",
    "from", "where", "and", "or", "order", "group", "by", "into", "values",
    "join", "left", "right", "inner", "outer", "on", "as", "offset", "distinct",
    "count", "sum", "avg", "max", "min", "view", "index"
  ).distinct.sorted

  override def complete(reader: LineReader, line: ParsedLine, candidates: util.List[Candidate]): Unit = {
    val word = Option(line.word()).getOrElse("")
    val prefix = word.toLowerCase
    val prev = previousWord(line)

    if prev == "use" then
      addMetaMatches(meta.schemas, prefix, candidates)
    else if tableTriggers.contains(prev) then
      if prev == "find" then addMatches(Seq("table ", "view "), prefix, candidates)
      addMetaMatches(meta.relations, prefix, candidates)
    else if line.wordIndex() == 0 then
      phrases.filter(_.toLowerCase.startsWith(prefix)).foreach(p => candidates.add(new Candidate(p)))
    else
      words.filter(_.toLowerCase.startsWith(prefix)).foreach(w => candidates.add(new Candidate(w)))
  }

  private def previousWord(line: ParsedLine): String = {
    val words = line.words().asScala
    val idx = line.wordIndex()
    if idx > 0 && idx <= words.size then words(idx - 1).toLowerCase else ""
  }

  private def addMetaMatches(items: Seq[String], prefix: String, candidates: util.List[Candidate]): Unit = {
    val matched = items.filter(_.toLowerCase.startsWith(prefix))
    matched.take(metaMaxCandidates).foreach(name => candidates.add(new Candidate(name)))
    val remaining = matched.size - metaMaxCandidates
    if remaining > 0 then
      // sort=MAX so this hint stays at the end; complete=false so it is not inserted
      candidates.add(new Candidate(
        s"... ($remaining more)",
        s"... ($remaining more)",
        null, null, null, null,
        false,
        Integer.MAX_VALUE
      ))
  }

  private def addMatches(items: Seq[String], prefix: String, candidates: util.List[Candidate]): Unit = {
    items.filter(_.toLowerCase.startsWith(prefix)).foreach(name => candidates.add(new Candidate(name)))
  }
}
