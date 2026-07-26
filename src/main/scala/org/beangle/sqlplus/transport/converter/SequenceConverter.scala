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

package org.beangle.sqlplus.transport.converter

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.jdbc.meta.Sequence
import org.beangle.sqlplus.SqlplusLogger
import org.beangle.sqlplus.transport.{Converter, StageReport, StageResult}

class SequenceConverter(val target: DefaultTableStore) extends Converter {

  private val sequenceMap = Collections.newMap[String, Sequence]

  override def payloadCount: Int = sequenceMap.size

  def add(ns: Iterable[Sequence]): Unit = {
    ns.foreach(x => sequenceMap.put(x.qualifiedName, x))
  }

  def reset(): Unit = {

  }

  private def reCreate(sequence: Sequence): Unit = {
    target.drop(sequence)
    target.create(sequence)
    SqlplusLogger.info(s"Recreate sequence ${sequence.qualifiedName}")
  }

  def start(): StageResult = {
    val targetEngine = target.engine
    val report = new StageReport("sequences", sequenceMap.size)
    if (!targetEngine.supportSequence) {
      SqlplusLogger.info(s"Target database ${targetEngine.getClass.getSimpleName} doesn't support sequence,replication omitted.")
      sequenceMap.values.foreach(_ => report.skipped())
      return report.result
    }
    val watch = new Stopwatch(true)
    val sequences = sequenceMap.values
    SqlplusLogger.info("Start sequence replication...")
    for (sequence <- sequences) {
      try {
        reCreate(sequence)
        report.succeeded(sequence.qualifiedName)
      } catch {
        case e: Exception =>
          report.failed(sequence.qualifiedName, e)
          SqlplusLogger.error(s"Recreate sequence ${sequence.qualifiedName} failed", e)
      }
    }
    SqlplusLogger.info(s"End ${sequences.size} sequence replication,using $watch")
    report.result
  }

}
