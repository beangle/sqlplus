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

package org.beangle.sqlplus.shell

import org.jline.reader.LineReader
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ShellCompleterTest extends AnyFunSpec with Matchers {

  describe("LineShell") {
    it("enables case-insensitive candidate matching") {
      val shell = new LineShell(meta = new ShellCompleter.Meta {
        override def schemas: Seq[String] = Seq("SYSTEM")

        override def relations: Seq[String] = Seq("SYSTEM.USERS")
      })
      try
        val readerField = classOf[LineShell].getDeclaredField("reader")
        readerField.setAccessible(true)
        val reader = readerField.get(shell).asInstanceOf[LineReader]
        reader.isSet(LineReader.Option.CASE_INSENSITIVE) shouldBe true
      finally shell.close()
    }
  }
}
