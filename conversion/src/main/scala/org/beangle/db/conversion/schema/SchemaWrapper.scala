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
package org.beangle.db.conversion.schema

import org.beangle.commons.collection.page.PageLimit
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.dialect.{ Dialect, SQL }
import org.beangle.data.jdbc.meta.{ MetadataLoader, Schema, Sequence, Table }
import org.beangle.data.jdbc.query.JdbcExecutor
import org.beangle.db.conversion.DataWrapper

import javax.sql.DataSource

class SchemaWrapper(val dataSource: DataSource, val dialect: Dialect, val schema: Schema)
  extends DataWrapper with Logging {
  val executor = new JdbcExecutor(dataSource)
  val loader = new MetadataLoader(dataSource.getConnection.getMetaData, dialect)

  def loadMetas(loadTableExtra: Boolean, loadSequence: Boolean): Unit = {
    loader.loadTables(schema, loadTableExtra)
    if (loadSequence) loader.loadSequences(schema)
  }

  override def has(table: Table): Boolean = {
    schema.getTable(table.name.value).isDefined
  }

  override def drop(table: Table): Boolean = {
    try {
      schema.getTable(table.name.value) foreach { t =>
        schema.tables.remove(t.name)
        executor.update(dialect.tableGrammar.dropCascade(t.qualifiedName))
      }
    } catch {
      case e: Exception =>
        logger.error(s"Drop table ${table.name} failed", e)
        return false
    }
    return true
  }

  override def create(table: Table): Boolean = {
    if (schema.getTable(table.name.value).isEmpty) {
      try {
        executor.update(SQL.createTable(table, dialect))
      } catch {
        case e: Exception =>
          logger.error(s"Cannot create table ${table.name}", e)
          return false
      }
    }
    true
  }

  def drop(sequence: Sequence): Boolean = {
    val exists = schema.sequences.contains(sequence)
    if (exists) {
      schema.sequences.remove(sequence)
      try {
        val dropSql = SQL.dropSequence(sequence, dialect)
        if (null != dropSql) executor.update(dropSql)
      } catch {
        case e: Exception =>
          logger.error(s"Drop sequence ${sequence.name} failed", e)
          return false
      }
    }
    true
  }

  def create(sequence: Sequence): Boolean = {
    try {
      val createSql = SQL.createSequence(sequence, dialect)
      if (null != createSql) executor.update(createSql)
    } catch {
      case e: Exception =>
        logger.error(s"cannot create sequence ${sequence.name}", e)
        return false
    }
    return true
  }

  def count(table: Table): Int = {
    executor.queryForInt("select count(*) from (" + SQL.query(table) + ") tb"
      + System.currentTimeMillis).get
  }

  def get(table: Table, limit: PageLimit): Seq[Array[Any]] = {
    val orderBy = new StringBuffer
    table.primaryKey foreach { pk =>
      if (pk.columns.size > 0) {
        orderBy.append(" order by ")
        orderBy.append(pk.columnNames.foldLeft("")(_ + "," + _).substring(1))
      }
    }

    val sql = SQL.query(table) + orderBy.toString
    val grammar = dialect.limitGrammar
    val rs = grammar.limit(sql, (limit.pageIndex - 1) * limit.pageSize, limit.pageSize)
    executor.query(rs._1, rs._2.toArray: _*)
  }

  def get(table: Table): Seq[Array[Any]] = {
    executor.query(SQL.query(table))
  }

  def save(table: Table, datas: Seq[Array[Any]]): Int = {
    val types = for (column <- table.columns) yield column.sqlType.code
    val insertSql = SQL.insert(table)
    executor.batch(insertSql, datas, types).length
  }

  def supportLimit = (null != dialect.limitGrammar)

  def close() {}
}
