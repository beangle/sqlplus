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

package org.beangle.sqlplus.lint.seq

import java.io.FileInputStream

import javax.sql.DataSource
import org.beangle.commons.logging.Logging
import org.beangle.jdbc.ds.DataSourceUtils
import org.beangle.sqlplus.lint.seq.impl.{DefaultSequenceNamePattern, OracleTableSequenceDao}

object SequenceChecker extends Logging {

  /**
   * SequenceChecker /path/to/dbconfig.xml info|update|remove
   * @param args
   */
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage: SequenceChecker /path/to/your/xml info|update|remove")
      return
    }
    val xml = scala.xml.XML.load(new FileInputStream(args(0)))
    val dataSource = getDataSource(xml)
    val action = if (args.length > 1) args(1) else "info"
    val update = (action == "update")
    val remove = (action == "remove")

    val tableSequenceDao = new OracleTableSequenceDao()
    tableSequenceDao.setDataSource(dataSource)
    tableSequenceDao.setRelation(new DefaultSequenceNamePattern())
    val sequences = tableSequenceDao.getInconsistent()
    info(sequences)
    if (update)
      adjust(tableSequenceDao, sequences)

    if (remove)
      drop(tableSequenceDao, sequences)

  }

  def drop(tableSequenceDao: TableSequenceDao, sequences: List[TableSequence]): Unit = {
    val ps = System.out
    if (sequences.nonEmpty)
      ps.println("start drop ...")
    for (seq <- sequences) {
      if (null == seq.tableName) {
        tableSequenceDao.drop(seq.seqName)
        ps.println("drop sequence " + seq.seqName)
      }
    }
  }

  def adjust(tableSequenceDao: TableSequenceDao, sequences: List[TableSequence]): Unit = {
    val ps = System.out
    if (sequences.nonEmpty) ps.println("start adjust ...")
    for (seq <- sequences) {
      if (null != seq.tableName) {
        ps.println("adjust sequence " + seq.seqName + " with lastnumber "
          + tableSequenceDao.adjust(seq))
      }
    }
    ps.println("finish adjust")
  }

  def info(sequences: List[TableSequence]): Unit = {
    val ps = System.out
    if (sequences.isEmpty) {
      ps.println("without any inconsistent  sequence")
    } else {
      ps.println("find inconsistent  sequence " + sequences.size)
      ps.println("sequence_name(lastnumber) table_name(max id)")
    }

    for (seq <- sequences)
      ps.println(seq)
  }

  private def getDataSource(xml: scala.xml.Node): DataSource = {
    val dbconf = DataSourceUtils.parseXml(xml)
    DataSourceUtils.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props)
  }
}
