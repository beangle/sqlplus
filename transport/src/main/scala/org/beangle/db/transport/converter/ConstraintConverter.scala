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
import org.beangle.data.jdbc.meta.{Constraint, ForeignKey}
import org.beangle.db.transport.Converter

class ConstraintConverter(val target: DefaultTableStore, val threads: Int) extends Converter with Logging {

  private val constraintMap = Collections.newMap[String, Constraint]

  override def payloadCount: Int = constraintMap.size

  def add(cs: Iterable[Constraint]): Unit = {
    cs foreach { c => constraintMap.put(c.literalName, c) }
  }

  def reset(): Unit = {

  }

  def start(): Unit = {
    val constraints = constraintMap.values
    val cnt = constraints.size
    val watch = new Stopwatch(true)
    logger.info(s"Start $cnt constraints replication in $threads threads...")
    ThreadWorkers.work(constraints, {
      case fk: ForeignKey =>
        val sql = target.engine.alterTable(fk.table).addForeignKey(fk)
        try {
          target.executor.update(sql)
          logger.info(s"Apply constraint ${fk.name}")
        } catch {
          case e: Exception => logger.warn(s"Cannot execute $sql")
        }
      case _ =>
    }, threads)
    logger.info(s"Finish $cnt constraints replication,using $watch")
  }
}
