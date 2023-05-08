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

package org.beangle.db.transport.schema

import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.meta.{Constraint, ForeignKey, PrimaryKey}
import org.beangle.db.transport.Converter

class ConstraintConverter(val source: SchemaWrapper, val target: SchemaWrapper) extends Converter with Logging {

  private val constraints = new collection.mutable.ListBuffer[Constraint]

  def addConstraints(newContraints: Seq[Constraint]): Unit = {
    constraints ++= newContraints
  }

  def reset(): Unit = {

  }

  def start(): Unit = {
    val watch = new Stopwatch(true)
    logger.info("Start constraint replication...")
    for (constraint <- constraints.sorted) {
      constraint match {
        case fk: ForeignKey =>
          val sql = target.engine.alterTable(fk.table).addForeignKey(fk)
          try {
            target.executor.update(sql)
            logger.info(s"Apply constraint ${fk.name}")
          } catch {
            case e: Exception => logger.warn(s"Cannot execute $sql")
          }
      }
    }
    logger.info(s"End constraint replication,using $watch")
  }
}
