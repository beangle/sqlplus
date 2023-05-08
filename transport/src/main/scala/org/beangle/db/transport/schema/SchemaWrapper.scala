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

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.engine.Engine
import org.beangle.data.jdbc.meta.{MetadataLoader, Schema, Sequence, Table}
import org.beangle.data.jdbc.query.{JdbcExecutor, ResultSetIterator}
import org.beangle.db.transport.DataWrapper

import java.sql.Connection
import javax.sql.DataSource

class SchemaWrapper(val dataSource: DataSource, val engine: Engine, val schema: Schema)
  extends DataWrapper with Logging {
  val executor = new JdbcExecutor(dataSource)

  def loadMetas(loadTableExtra: Boolean, loadSequence: Boolean): Unit = {
    var conn: Connection = null
    try {
      conn = dataSource.getConnection
      val loader = new MetadataLoader(conn.getMetaData, engine)
      loader.loadTables(schema, loadTableExtra)
      if (loadSequence) loader.loadSequences(schema)
    } finally {
      IOs.close(conn)
    }
  }

  def createSchema(): Unit = {
    val loader = new MetadataLoader(dataSource.getConnection.getMetaData, engine)
    val schemas = loader.schemas()
    if (!schemas.map(_.toLowerCase).contains(schema.name.value.toLowerCase)) {
      val createSchemaSql = engine.createSchema(schema.name.toString)
      if Strings.isNotBlank(createSchemaSql) then executor.update(createSchemaSql)
    }
  }

  override def has(table: Table): Boolean = {
    schema.getTable(table.name.value).isDefined
  }

  override def get(table: Table): Option[Table] = {
    schema.getTable(table.name.value)
  }

  override def clean(table: Table): Boolean = {
    get(table) match {
      case None => create(table)
      case Some(t) =>
        if table.isSameStruct(t) then cleanSelfKeys(t)
        else {
          drop(table)
          create(table)
        }
    }
    schema.addTable(table)
    true
  }

  override def cleanForeignKeys(table: Table): Unit = {
    get(table) foreach { t =>
      //drop foreign keys first may cause some index dropped.
      //so put them before index drop.
      t.foreignKeys foreach { fk =>
        try {
          executor.update(engine.alterTable(table).dropConstraint(fk.literalName))
        } catch {
          case e: Throwable => //may be cascade drop by other table.
        }
        logger.debug(s"Drop foreign key ${fk.literalName} on ${table.qualifiedName}.")
      }
    }
  }

  private def cleanSelfKeys(table: Table): Unit = {
    try
      schema.getTable(table.name.value) foreach { t =>
        t.primaryKey foreach { pk =>
          executor.update(engine.alterTable(t).dropPrimaryKey(pk))
          logger.debug(s"Drop primary key ${table.qualifiedName}.${pk.literalName}")
        }
        t.uniqueKeys foreach { uk =>
          executor.update(engine.alterTable(table).dropConstraint(uk.literalName))
          logger.debug(s"Drop unique key ${uk.literalName} on ${table.qualifiedName}.")
        }
        t.indexes foreach { i =>
          try {
            executor.update(engine.dropIndex(i))
          } catch {
            case e: Throwable => //may be cascade drop by other foreign keys.
          }
          logger.debug(s"Drop index ${i.literalName} on ${table.qualifiedName}.")
        }
      }
      logger.info(s"Clean table ${table.qualifiedName}'s keys and constraints")
    catch
      case e: Exception => logger.error(s"Clean table ${table.name} 's keys failed", e)
  }

  override def truncate(table: Table): Boolean = {
    try
      schema.getTable(table.name.value) foreach { t =>
        executor.update(engine.truncate(t))
        logger.info(s"Truncate table ${table.name}")
      }
      true
    catch
      case e: Exception =>
        logger.error(s"Truncate table ${table.name} failed", e)
        false
  }

  override def drop(table: Table): Boolean = {
    try
      schema.getTable(table.name.value) foreach { t =>
        schema.tables.remove(t.name)
        executor.update(engine.dropTable(t.qualifiedName))
        logger.info(s"Drop table ${table.name}")
      }
      true
    catch
      case e: Exception =>
        logger.error(s"Drop table ${table.name} failed", e)
        false
  }

  override def create(table: Table): Boolean = {
    if (schema.getTable(table.name.value).isEmpty) {
      try
        executor.update(engine.createTable(table))
        logger.info(s"Create table ${table.name}")
      catch
        case e: Exception =>
          logger.error(s"Cannot create table ${table.name}", e)
          return false
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

  override def count(table: Table): Int = {
    executor.queryForInt("select count(*) from " + table.qualifiedName + " tb").get
  }

  override def select(table: Table): ResultSetIterator = {
    executor.iterate(engine.query(table))
  }

  override def save(table: Table, datas: collection.Seq[Array[_]]): Int = {
    val types = for (column <- table.columns) yield column.sqlType.code
    val insertSql = engine.insert(table)
    executor.batch(insertSql, datas, types.toSeq).length
  }

  override def close(): Unit = {}
}
