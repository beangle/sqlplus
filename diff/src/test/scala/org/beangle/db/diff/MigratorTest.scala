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

import org.beangle.data.jdbc.dialect.Dialects
import org.beangle.data.jdbc.meta.{Column, Database}
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MigratorTest extends AnyFunSpec with Matchers {
  describe("Migrator") {
    it("test diff") {
      val migrator = new Migrator
      val engine = Engines.PostgreSQL
      val dialect = Dialects.forName(engine.name)
      val newer = new Database(engine)
      val older = new Database(engine)

      val booleanType = engine.toType(Types.BOOLEAN)
      val timestampType = engine.toType(Types.TIMESTAMP)
      val id1 = new Column("id", engine.toType(Types.BIGINT))
      val id2 = new Column("id", engine.toType(Types.INTEGER))
      val name = new Column("name", engine.toType(Types.VARCHAR, 200))
      val code1 = new Column("code", engine.toType(Types.VARCHAR, 20))
      code1.nullable = false
      val code2 = new Column("code", engine.toType(Types.VARCHAR, 20))

      val user1 = newer.getOrCreateSchema("test").createTable("users")
      val user2 = older.getOrCreateSchema("test").createTable("users")

      user1.add(id1, name, code1)
      user1.comment = Some("用户信息")
      val age = user1.createColumn("updated_at", timestampType)
      age.nullable = false
      age.defaultValue = Some("now()")
      var enabled1 = user1.createColumn("enabled", booleanType)
      enabled1.defaultValue = Some("true")
      enabled1.nullable = false
      enabled1.comment = Some("是否启用")

      user1.createUniqueKey("uk_user_code", "code")
      user1.createIndex("idx_user_name", false, "name")
      user1.createPrimaryKey("", "id")

      user2.add(id2, name, code2)
      user2.createColumn("enabled", booleanType)
      user2.createUniqueKey("uk_user_name", "name")
      user2.createColumn("remark", engine.toType(Types.VARCHAR, 200))
      user2.createIndex("idx_user_code", false, "code")
      val pk2 = user2.createPrimaryKey("", "code")
      val diff = migrator.sql(new Diff().diff(newer, older))
      println(dialect.createTable(user2))
      println(dialect.alterTableAddPrimaryKey(user2, pk2))
      println(diff.mkString(";\n"))
    }
  }
}
