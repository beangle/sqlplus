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

import org.beangle.jdbc.script.{Directive, OracleParser}
import org.h2.jdbcx.JdbcDataSource
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SqlActionTest extends AnyFunSpec, Matchers {

  describe("SqlAction") {
    it("defaults loop protection to fifty batches") {
      val statements = OracleParser.parse("-- @loop import target_data\ninsert into t select * from s;")
      val directive = statements.head.directive(Directive.Loop).get
      directive.param("batch-size") shouldBe None
      directive.param("max-batches") shouldBe None
    }

    it("rejects loop directives on non-insert statements") {
      val ds = new JdbcDataSource
      ds.setURL("jdbc:h2:mem:invalid_loop;DB_CLOSE_DELAY=-1")
      val action = new SqlAction(
        ds, OracleParser.parse("-- @loop batch-size=3\nupdate target_data set id=id;"), ignoreError = false)
      an[IllegalArgumentException] should be thrownBy action.process()
    }

    it("rejects an explicit limit because the loop adds it") {
      val ds = new JdbcDataSource
      ds.setURL("jdbc:h2:mem:explicit_limit;DB_CLOSE_DELAY=-1")
      val action = new SqlAction(
        ds, OracleParser.parse("-- @loop batch-size=3\ninsert into t select * from s limit 3;"), ignoreError = false)
      val error = the[IllegalArgumentException] thrownBy action.process()
      error.getMessage should include("adds LIMIT automatically")
    }

    it("returns failure while continuing later best-effort statements") {
      val ds = new JdbcDataSource
      ds.setURL("jdbc:h2:mem:action_failure;DB_CLOSE_DELAY=-1")
      val conn = ds.getConnection
      try conn.createStatement().execute("create table action_result(id int primary key)")
      finally conn.close()

      val sql =
        """insert into missing_table values(1);
          |insert into action_result values(1);""".stripMargin
      new SqlAction(ds, OracleParser.parse(sql)).process() shouldBe false

      val verify = ds.getConnection
      try {
        val rs = verify.createStatement().executeQuery("select count(*) from action_result")
        rs.next() shouldBe true
        rs.getInt(1) shouldBe 1
      } finally {
        verify.close()
      }
    }

    it("repeats explicitly marked insert statements in committed batches") {
      val ds = new JdbcDataSource
      ds.setURL("jdbc:h2:mem:sql_action;DB_CLOSE_DELAY=-1")
      val conn = ds.getConnection
      try {
        conn.createStatement().execute("create table source_data(id int primary key)")
        conn.createStatement().execute("create table target_data(id int primary key)")
        conn.createStatement().execute(
          "insert into source_data select x from system_range(1, 7)")
      } finally {
        conn.close()
      }

      val sql =
        """-- @loop batch-size=3 max-batches=10 import target_data
          |insert into target_data(id)
          |select s.id from source_data s
          |where not exists(select 1 from target_data t where t.id=s.id)
          |;""".stripMargin
      new SqlAction(ds, OracleParser.parse(sql)).process() shouldBe true

      val verify = ds.getConnection
      try {
        val rs = verify.createStatement().executeQuery("select count(*) from target_data")
        rs.next() shouldBe true
        rs.getInt(1) shouldBe 7
      } finally {
        verify.close()
      }
    }
  }
}
