/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.db.lint.seq.impl

import org.beangle.data.jdbc.query.JdbcExecutor
import org.beangle.commons.logging.Logging
import org.beangle.db.lint.seq.SequenceNamePattern
import org.beangle.db.lint.seq.TableSequence
import org.beangle.db.lint.seq.TableSequenceDao

import javax.sql.DataSource

class OracleTableSequenceDao extends TableSequenceDao with Logging {

  private var jdbcExecutor: JdbcExecutor = _

  private var relation: SequenceNamePattern = _

  def drop(sequence_name: String): Boolean = {
    val sql = "drop sequence " + sequence_name
    jdbcExecutor.update(sql)
    true
  }

  def getInconsistent(): List[TableSequence] = {
    val err_seqs = new collection.mutable.ListBuffer[TableSequence]
    val list = getAllNames()
    for (seqName <- list) {
      val tempSeqSql = "select last_number from user_sequences seqs where seqs.sequence_name='" + seqName + "'"
      val seqLast_number = jdbcExecutor.queryForLong(tempSeqSql).get
      val tableName = relation.getTableName(seqName)
      val exists = jdbcExecutor.queryForInt("select count(*) from user_tables tbl where tbl.table_name='"
        + tableName + "'").get > 0
      if (exists) {
        val dataCount = jdbcExecutor.queryForLong("select count(*) from " + tableName).get
        if (dataCount > 0) {
          var tableLMaxId = -2L
          try {
            tableLMaxId = jdbcExecutor.queryForLong("select max(id) from  " + tableName).get
          } catch {
            case e: Exception => logger.warn(s"cannot find table $tableName")
          }
          if (seqLast_number < tableLMaxId) {
            val seq = new TableSequence()
            seq.seqName = seqName
            seq.tableName = tableName
            seq.lastNumber = seqLast_number
            seq.maxId = tableLMaxId
            err_seqs += seq
          }
        }
      } else {
        val seq = new TableSequence()
        seq.seqName = seqName
        seq.lastNumber = seqLast_number
        err_seqs += seq
      }
    }
    err_seqs.toList
  }

  /**
   * @param sequence
   * @param table
   * @param column
   */
  def adjust(tableSequence: TableSequence): Long = {
    val sequence = tableSequence.seqName
    val getSql = "select " + sequence + ".nextval from dual"
    val current = jdbcExecutor.queryForLong(getSql).get
    val countSql = "select max(" + tableSequence.idColumnName + ") maxid from " + tableSequence.tableName
    val rs = jdbcExecutor.query(countSql)
    var max = 0L
    if (!rs.isEmpty) {
      max = rs.head.head.asInstanceOf[Number].longValue
    }
    var repaired = 0L
    var updateIncrease: String = null
    if (max > current) {
      if (max - current > 1) {
        jdbcExecutor.update("ALTER SEQUENCE " + sequence + " INCREMENT BY   " + (max - current - 1))
        jdbcExecutor.queryForLong(getSql)
        jdbcExecutor.update("ALTER SEQUENCE " + sequence + " INCREMENT BY  1")
      }
      repaired = jdbcExecutor.queryForLong(getSql).get
    } else {
      if (1 == current) return 1L
      jdbcExecutor.update("ALTER SEQUENCE " + sequence + " INCREMENT BY  -1")
      repaired = jdbcExecutor.queryForLong(getSql).get
      jdbcExecutor.update("ALTER SEQUENCE " + sequence + " INCREMENT BY  1")
    }
    return repaired
  }

  def getAllNames(): List[String] = {
    val sql = "select sequence_name from user_sequences order by sequence_name"
    val seqs = jdbcExecutor.query(sql)
    val sequenceNames = new collection.mutable.ListBuffer[String]
    for (data <- seqs) {
      sequenceNames += data.head.asInstanceOf[String]
    }
    sequenceNames.toList
  }

  def getNoneReferenced(): List[String] = {
    val err_seqs = new collection.mutable.ListBuffer[String]
    val list = getAllNames()
    for (seqName <- list) {
      val tableName = relation.getTableName(seqName)
      val exists = jdbcExecutor.queryForInt("select count(*) from user_tables tbl where tbl.table_name='"
        + tableName + "'").get > 0
      if (!exists)
        err_seqs += seqName
    }
    err_seqs.toList
  }

  def setRelation(relation: SequenceNamePattern) {
    this.relation = relation
    this.relation.init()
  }

  def setDataSource(source: DataSource) {
    this.jdbcExecutor = new JdbcExecutor(source);
  }
}
