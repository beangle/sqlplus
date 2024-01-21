package org.beangle.db.transport

import org.beangle.commons.lang.ClassLoaders
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.must.Matchers

class ConfigTest extends AnyFunSpec, Matchers {
  describe("Config") {
    it("parse") {
      val xml = scala.xml.XML.load(ClassLoaders.getResourceAsStream("h2h2.xml").get)
      val config = Config(xml)
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
