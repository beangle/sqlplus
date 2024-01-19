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

package org.beangle.db.transport.converter

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.meta.Sequence
import org.beangle.db.transport.Converter

class SequenceConverter(val target: DefaultTableStore) extends Converter with Logging {

  private val sequenceMap = Collections.newMap[String, Sequence]

  def add(ns: Iterable[Sequence]): Unit = {
    ns.foreach(x => sequenceMap.put(x.qualifiedName, x))
  }

  def reset(): Unit = {

  }

  private def reCreate(sequence: Sequence): Boolean = {
    if (target.drop(sequence)) {
      if (target.create(sequence)) {
        logger.info(s"Recreate sequence ${sequence.qualifiedName}")
        return true
      } else {
        logger.error(s"Recreate sequence ${sequence.qualifiedName} failure.")
      }
    }
    false
  }

  def start(): Unit = {
    val targetEngine = target.engine
    if (!targetEngine.supportSequence) {
      logger.info(s"Target database ${targetEngine.getClass.getSimpleName} doesn't support sequence,replication omitted.")
      return
    }
    val watch = new Stopwatch(true)
    val sequences = sequenceMap.values
    logger.info("Start sequence replication...")
    for (sequence <- sequences) {
      reCreate(sequence)
    }
    logger.info(s"End ${sequences.size} sequence replication,using $watch")
  }

}
