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

package org.beangle.sqlplus.transport

import java.io.File
import java.sql.{Connection, DriverManager, ResultSet, ResultSetMetaData, SQLException, Types}
import java.util.concurrent.Executors
import javax.sql.DataSource
import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

import org.duckdb.{DuckDBAppender, DuckDBConnection}
import org.slf4j.{Logger, LoggerFactory}

/**
 * 多线程把源库表数据通过 DuckDBAppender 写入本地 DuckDB 文件。
 *
 * 所有 worker 线程共享同一个 DuckDB 实例(通过 DuckDBConnection.duplicate() 派生独立 connection)。
 * DuckDB 文件库对多个 DriverManager.getConnection 拒绝("Unique file handle conflict"),
 * 必须用 duplicate() 在同一实例下创建多 connection。
 */
class DuckDBDumper(source: DataSource, duckdbFile: File, threadCount: Int = Runtime.getRuntime.availableProcessors()) {
  private val logger: Logger = LoggerFactory.getLogger(classOf[DuckDBDumper])

  def dumpAll(schemaNames: Seq[String]): Unit = {
    val tables = listTables(schemaNames)
    if tables.isEmpty then
      logger.warn("No tables found to dump")
      return
    logger.info(s"Dumping ${tables.size} tables into ${duckdbFile.getAbsolutePath} with $threadCount threads")

    // 主 connection,所有 worker 通过 duplicate() 派生自己的 connection
    val mainConn = DriverManager.getConnection(s"jdbc:duckdb:${duckdbFile.getAbsolutePath}")
      .asInstanceOf[DuckDBConnection]
    try {
      createSchemas(mainConn, schemaNames)

      val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(threadCount))
      try {
        val futures = tables.map { case (schema, table) =>
          Future {
            try
              dumpTable(mainConn, schema, table)
              logger.info(s"  dumped $schema.$table")
            catch
              case e: Exception =>
                logger.error(s"  failed to dump $schema.$table: ${e.getMessage}", e)
          }(ec)
        }
        Await.result(Future.sequence(futures)(implicitly, ec), Duration.Inf)
      } finally {
        ec.shutdown()
      }

      // 关闭前做 CHECKPOINT + VACUUM,清理 dump 过程产生的 free blocks / 碎片
      val cleanupStmt = mainConn.createStatement()
      try {
        cleanupStmt.execute("CHECKPOINT")
        cleanupStmt.execute("VACUUM")
        logger.info(s"  checkpoint + vacuum done")
      } finally {
        cleanupStmt.close()
      }
    } finally {
      mainConn.close()
    }
  }

  private def createSchemas(mainConn: Connection, schemaNames: Seq[String]): Unit = {
    val stmt = mainConn.createStatement()
    for (schema <- schemaNames) {
      stmt.execute(s"""CREATE SCHEMA IF NOT EXISTS "$schema"""")
    }
    stmt.close()
  }

  private def listTables(schemaNames: Seq[String]): Seq[(String, String)] = {
    var conn: Connection = null
    try {
      conn = source.getConnection
      val meta = conn.getMetaData
      val catalog = conn.getCatalog
      schemaNames.flatMap { schema =>
        val rs = meta.getTables(catalog, schema, null, Array("TABLE"))
        val buf = mutable.Buffer.empty[(String, String)]
        while rs.next() do
          val name = rs.getString("TABLE_NAME")
          if !name.contains("$") then buf += ((schema, name))
        rs.close()
        buf.toSeq.sortBy(_._2)
      }
    } finally {
      if conn != null then conn.close()
    }
  }

  private def dumpTable(mainConn: DuckDBConnection, schema: String, table: String): Unit = {
    var srcConn: Connection = null
    var duckConn: Connection = null
    var appender: DuckDBAppender = null
    try {
      srcConn = source.getConnection
      val rs = srcConn.createStatement().executeQuery(s"""SELECT * FROM "$schema"."$table" """)
      val rsmd = rs.getMetaData
      val colCount = rsmd.getColumnCount

      // 在 worker 线程里从主 connection duplicate 出独立 connection
      duckConn = mainConn.duplicate()
      val duckStmt = duckConn.createStatement()
      duckStmt.execute(s"""DROP TABLE IF EXISTS "$schema"."$table"""")
      duckStmt.execute(buildCreateTableSQL(schema, table, rsmd))
      duckStmt.close()

      appender = duckConn.asInstanceOf[DuckDBConnection].createAppender(schema, table)
      while rs.next() do
        appender.beginRow()
        for i <- 1 to colCount do
          appendValue(appender, rs, i, rsmd.getColumnType(i))
        appender.endRow()
      appender.flush()
    } finally {
      if appender != null then try appender.close() catch case _: Exception => ()
      if duckConn != null then duckConn.close()
      if srcConn != null then srcConn.close()
    }
  }

  private def buildCreateTableSQL(schema: String, table: String, rsmd: ResultSetMetaData): String = {
    val cols = (1 to rsmd.getColumnCount).map { i =>
      val colName = rsmd.getColumnLabel(i)
      val duckType = duckdbType(rsmd.getColumnType(i), rsmd.getPrecision(i), rsmd.getScale(i))
      s""""$colName" $duckType"""
    }
    s"""CREATE TABLE "$schema"."$table" (${cols.mkString(", ")})"""
  }

  private def duckdbType(jdbcType: Int, precision: Int, scale: Int): String = {
    jdbcType match {
      case Types.SMALLINT | Types.TINYINT | Types.INTEGER => "INTEGER"
      case Types.BIGINT                                     => "BIGINT"
      case Types.REAL | Types.FLOAT                          => "FLOAT"
      case Types.DOUBLE                                     => "DOUBLE"
      case Types.DECIMAL | Types.NUMERIC                     =>
        val p = if precision == 0 then 38 else Math.min(precision, 38)
        val s = Math.min(Math.max(scale, 0), p)
        s"DECIMAL($p,$s)"
      case Types.BOOLEAN | Types.BIT                         => "BOOLEAN"
      case Types.CHAR | Types.VARCHAR | Types.LONGVARCHAR
           | Types.NCHAR | Types.NVARCHAR | Types.LONGNVARCHAR
           | Types.CLOB | Types.NCLOB                        => "VARCHAR"
      case Types.DATE                                       => "DATE"
      case Types.TIME | Types.TIME_WITH_TIMEZONE             => "TIME"
      case Types.TIMESTAMP | Types.TIMESTAMP_WITH_TIMEZONE   => "TIMESTAMP"
      case Types.BINARY | Types.VARBINARY | Types.LONGVARBINARY | Types.BLOB => "BLOB"
      case _                                                 => "VARCHAR"
    }
  }

  private def appendValue(appender: DuckDBAppender, rs: ResultSet, i: Int, jdbcType: Int): Unit = {
    // 1.5.5 起 Appender 严格检查列类型,必须按列类型用对应的 append 方法
    // 通用 helper:read 触发 wasNull 状态,然后按结果选择 appendNull 或 appendFn
    def appendOption[T](read: => T)(appendFn: T => Unit): Unit = {
      val v = read
      if rs.wasNull() then appender.appendNull() else appendFn(v)
    }

    jdbcType match {
      case Types.SMALLINT | Types.TINYINT | Types.INTEGER =>
        appendOption(rs.getInt(i))(appender.append(_))
      case Types.BIGINT =>
        appendOption(rs.getLong(i))(appender.append(_))
      case Types.REAL | Types.FLOAT =>
        appendOption(rs.getFloat(i))(appender.append(_))
      case Types.DOUBLE =>
        appendOption(rs.getDouble(i))(appender.append(_))
      case Types.DECIMAL | Types.NUMERIC =>
        appendOption(rs.getBigDecimal(i))(appender.append(_))
      case Types.BOOLEAN | Types.BIT =>
        appendOption(rs.getBoolean(i))(appender.append(_))
      case Types.DATE =>
        appendOption(rs.getDate(i))(d => appender.append(d.toLocalDate))
      case Types.TIME | Types.TIME_WITH_TIMEZONE =>
        appendOption(rs.getTime(i))(t => appender.append(t.toLocalTime))
      case Types.TIMESTAMP | Types.TIMESTAMP_WITH_TIMEZONE =>
        appendOption(rs.getTimestamp(i))(t => appender.append(t.toLocalDateTime))
      case Types.BINARY | Types.VARBINARY | Types.LONGVARBINARY | Types.BLOB =>
        appendOption(rs.getBytes(i))(appender.append(_))
      case _ =>
        appendOption(rs.getString(i))(appender.append(_))
    }
  }
}
