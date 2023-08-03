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

package org.beangle.db.lint.validator

import org.beangle.commons.io.Files
import org.beangle.data.jdbc.ds.{DataSourceFactory, DataSourceUtils}
import org.beangle.data.jdbc.engine.Engines
import org.beangle.data.jdbc.meta.{Database, Diff, MetadataLoader, Serializer}

import java.io.{File, FileInputStream}

object SchemaValidator {

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage: Reactor /path/to/your/validator.xml");
      return
    }
    val xml = scala.xml.XML.load(new FileInputStream(args(0)))

    val dbconf = DataSourceUtils.parseXml((xml \\ "db").head)
    var basisFile = (xml \\ "basis" \ "@file").text
    if (!basisFile.contains("/") && !basisFile.contains("\\")) {
      basisFile = new File(args(0)).getAbsoluteFile.getParentFile.getAbsolutePath + Files./ + basisFile
    }
    if (!new File(basisFile).exists()) {
      println("Cannot find basis xml file " + basisFile)
      return
    }
    val basis = Serializer.fromXml(Files.readString(new File(basisFile)))

    val ds = DataSourceFactory.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props)
    val engine = Engines.forDataSource(ds)

    val database = new Database(engine)
    val metaloader = MetadataLoader(ds.getConnection.getMetaData, engine)
    basis.schemas foreach { s =>
      val schema = database.getOrCreateSchema(s._1.value)
      metaloader.loadTables(schema, true)
    }
    val diff = Diff.diff(database, basis)
    val sqls = Diff.sql(diff)
    if (sqls.isEmpty) println(ok("OK:") + "database and xml are coincident.")
    else
      println(warn("WARN:") + "database and xml are NOT coincident, and Referential migration sql are listed blow:")
      println(sqls.mkString(";\n"))
  }

  private def ok(msg: String): String = {
    s"\u001B[32m${msg}\u001B[0m"
  }

  private def warn(msg: String): String = {
    s"\u001B[31m${msg}\u001B[0m"
  }
}
