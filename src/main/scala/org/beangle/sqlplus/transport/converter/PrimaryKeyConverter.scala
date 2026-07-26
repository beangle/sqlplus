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
import org.beangle.commons.concurrent.Workers
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.jdbc.meta.PrimaryKey
import org.beangle.sqlplus.SqlplusLogger
import org.beangle.sqlplus.transport.{Converter, StageReport, StageResult}

class PrimaryKeyConverter(val target: DefaultTableStore, threads: Int) extends Converter {

  private val primaryKeyMap = Collections.newMap[String, PrimaryKey]

  def add(newPks: Iterable[PrimaryKey]): Unit = {
    newPks.foreach { pk => primaryKeyMap.put(s"${pk.table.qualifiedName}.${pk.literalName}", pk) }
  }

  override def payloadCount: Int = primaryKeyMap.size

  def reset(): Unit = {
  }

  def start(): StageResult = {
    val watch = new Stopwatch(true)
    val pks = primaryKeyMap.values
    val report = new StageReport("primary keys", pks.size)
    SqlplusLogger.info(s"Start ${pks.size} primary keys replication in $threads threads...")
    Workers.workOn(pks, threads) { pk =>
      try {
        val sql = target.engine.alterTable(pk.table).addPrimaryKey(pk)
        target.executor.update(sql)
        report.succeeded(s"${pk.table.qualifiedName}.${pk.literalName}")
        SqlplusLogger.info(s"Apply ${pk.name}(${pk.table.qualifiedName})")
      } catch {
        case e: Exception =>
          report.failed(s"${pk.table.qualifiedName}.${pk.literalName}", e)
          SqlplusLogger.error(s"Cannot apply primary key ${pk.literalName}", e)
      }
    }
    SqlplusLogger.info(s"Finish ${pks.size} primary keys replication,using $watch")
    report.result
  }

}
