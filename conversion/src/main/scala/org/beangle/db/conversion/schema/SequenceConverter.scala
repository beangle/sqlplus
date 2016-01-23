/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.db.conversion.schema

import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.db.conversion.Converter
import org.beangle.data.jdbc.meta.Sequence

class SequenceConverter(val source: SchemaWrapper, val target: SchemaWrapper) extends Converter with Logging {

  val sequences = new collection.mutable.ListBuffer[Sequence]

  def reset() {

  }

  private def reCreate(sequence: Sequence): Boolean = {
    if (target.drop(sequence)) {
      if (target.create(sequence)) {
        logger.info(s"Recreate sequence ${sequence.qualifiedName}")
        return true
      } else {
        logger.error(s"Recreate sequence {sequence.qualifiedName} failure.")
      }
    }
    return false
  }

  def start() {
    val targetDialect = target.dialect
    if (null == targetDialect.sequenceGrammar) {
      logger.info(s"Target database ${targetDialect.getClass().getSimpleName()} dosen't support sequence,replication ommited.")
      return
    }
    val watch = new Stopwatch(true)
    logger.info("Start sequence replication...")
    for (sequence <- sequences.sorted) {
      reCreate(sequence)
    }
    logger.info(s"End ${sequences.length} sequence replication,using $watch")
  }

  def addAll(newSequences: collection.Iterable[Sequence]) {
    sequences ++= newSequences
  }
}
