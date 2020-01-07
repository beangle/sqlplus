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
package org.beangle.db.transport.schema

import javax.sql.DataSource
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.engine.Engine
import org.beangle.data.jdbc.meta.{MetadataLoader, Schema, Sequence, Table}
import org.beangle.data.jdbc.query.{JdbcExecutor, ResultSetIterator}
import org.beangle.db.transport.DataWrapper

class SchemaWrapper(val dataSource: DataSource, val engine: Engine, val schema: Schema)
  extends DataWrapper with Logging {
  val executor = new JdbcExecutor(dataSource)
  val loader = new MetadataLoader(dataSource.getConnection.getMetaData, engine)

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
        executor.update(engine.dropTable(t.qualifiedName))
      }
      true
    } catch {
      case e: Exception =>
        logger.error(s"Drop table ${table.name} failed", e)
        false
    }
  }

  override def create(table: Table): Boolean = {
    if (schema.getTable(table.name.value).isEmpty) {
      try {
        executor.update(engine.createTable(table))
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
        val dropSql = engine.dropSequence(sequence)
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
      val createSql = engine.createSequence(sequence)
      if (null != createSql) executor.update(createSql)
      true
    } catch {
      case e: Exception =>
        logger.error(s"cannot create sequence ${sequence.name}", e)
        false
    }
  }

  def count(table: Table): Int = {
    executor.queryForInt("select count(*) from " + table.qualifiedName + " tb").get
  }

  def get(table: Table): ResultSetIterator = {
    executor.iterate(engine.query(table))
  }

  def save(table: Table, datas: collection.Seq[Array[_]]): Int = {
    val types = for (column <- table.columns) yield column.sqlType.code
    val insertSql = engine.insert(table)
    executor.batch(insertSql, datas.toSeq, types.toSeq).length
  }

  def close(): Unit = {}
}
