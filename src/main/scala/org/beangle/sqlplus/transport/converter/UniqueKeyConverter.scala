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
import org.beangle.jdbc.meta.UniqueKey
import org.beangle.sqlplus.SqlplusLogger
import org.beangle.sqlplus.transport.{Converter, StageReport, StageResult}

/** Restores unique keys before foreign keys that may reference them. */
class UniqueKeyConverter(val target: DefaultTableStore, val threads: Int) extends Converter {

  private val uniqueKeyMap = Collections.newMap[String, UniqueKey]

  override def payloadCount: Int = uniqueKeyMap.size

  def add(uniqueKeys: Iterable[UniqueKey]): Unit = {
    uniqueKeys.foreach { uk =>
      uniqueKeyMap.put(s"${uk.table.qualifiedName}.${uk.literalName}", uk)
    }
  }

  override def reset(): Unit = uniqueKeyMap.clear()

  override def start(): StageResult = {
    val uniqueKeys = uniqueKeyMap.values
    val report = new StageReport("unique keys", uniqueKeys.size)
    val watch = new Stopwatch(true)
    SqlplusLogger.info(s"Start ${uniqueKeys.size} unique keys replication in $threads threads...")
    Workers.workOn(uniqueKeys, threads) { uk =>
      val item = s"${uk.table.qualifiedName}.${uk.literalName}"
      val sql = target.engine.alterTable(uk.table).addUnique(uk)
      try {
        target.executor.update(sql)
        report.succeeded(item)
        SqlplusLogger.info(s"Apply unique key ${uk.name}")
      } catch {
        case e: Exception =>
          report.failed(item, e)
          SqlplusLogger.warn(s"Cannot execute $sql")
      }
    }
    SqlplusLogger.info(s"Finish ${uniqueKeys.size} unique keys replication,using $watch")
    report.result
  }
}
