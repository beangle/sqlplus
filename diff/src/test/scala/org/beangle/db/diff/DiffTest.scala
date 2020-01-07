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
package org.beangle.db.diff

import java.sql.Types

import org.beangle.data.jdbc.meta.{Column, Identifier, Table}
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DiffTest extends AnyFunSpec with Matchers {
  describe("Diff") {
    it("column diff") {
      val engine = Engines.PostgreSQL
      val column1 = new Column("id", engine.toType(Types.VARCHAR, 30))
      val column2 = new Column("id", engine.toType(Types.VARCHAR, 31))
      column1 should not be column2
    }

    it("table diff") {
      val engine = Engines.PostgreSQL
      val id1 = new Column("id", engine.toType(Types.BIGINT))
      val id2 = new Column("id", engine.toType(Types.INTEGER))
      val name = new Column("name", engine.toType(Types.VARCHAR, 200))
      val code1 = new Column("code", engine.toType(Types.VARCHAR, 20))
      val code2 = new Column("code", engine.toType(Types.VARCHAR, 20))
      code2.unique = true

      val table1 = new Table(null, Identifier("users"))
      val table2 = new Table(null, Identifier("users"))
      table1.add(id1, name, code1)
      table2.add(id2, name, code2)

      val rs = new Diff().diff(table1, table2)
      rs shouldBe defined
      rs foreach { tableDiff =>
        tableDiff.columns.updated should have size 2
        println(tableDiff.columns)
      }
    }
  }
}
