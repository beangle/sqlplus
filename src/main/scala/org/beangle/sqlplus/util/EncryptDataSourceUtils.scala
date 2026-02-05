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

package org.beangle.sqlplus.util

import org.beangle.commons.config.Config
import org.beangle.commons.xml.Node
import org.beangle.jdbc.ds.{DataSourceUtils, DatasourceConfig}

object EncryptDataSourceUtils {

  def parseXml(xml: Node): DatasourceConfig = {
    val cfg = DataSourceUtils.parseXml(xml)

    encryptor foreach { e =>
      cfg.user = e.process("user", cfg.user)
      cfg.password = e.process("password", cfg.password)
      cfg.props = cfg.props.map { case (k, v) =>
        (k, e.process(null, v))
      }
    }
    cfg
  }

  def encryptor: Option[Config.Processor] = {
    val key = "beangle.encryptor.password"
    var pwd = System.getProperty(key)
    if (null == pwd) {
      pwd = System.getenv("BEANGLE_ENCRYPTOR_PASSWORD")
    }
    if (null == pwd) None else Some(Config.pbe(pwd))
  }
}
