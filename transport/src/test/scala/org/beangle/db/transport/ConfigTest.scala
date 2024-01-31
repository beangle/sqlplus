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

package org.beangle.db.transport

import org.beangle.commons.lang.ClassLoaders
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.must.Matchers

import java.io.{File, FileInputStream}

class ConfigTest extends AnyFunSpec, Matchers {
  describe("Config") {
    it("parse") {
      val file = new File(ClassLoaders.getResource("h2h2.xml").get.toURI)
      val config = Config(file.getParent, new FileInputStream(file))
      assert(config.beforeActions.size == 1)
      val action1 = config.beforeActions.head
      assert(action1.contents.isEmpty)
      assert(action1.properties.contains("file"))
      assert(action1.properties("file") == "/tmp/prepare.sql")

      assert(config.afterActions.size == 1)
      val action = config.afterActions.head
      assert(action.contents.nonEmpty)
      assert(action.contents.head == "select count(*) from ems1.usr_users;")

    }
  }

}
