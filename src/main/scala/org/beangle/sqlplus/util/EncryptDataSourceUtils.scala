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

import org.beangle.jdbc.ds.{DataSourceUtils, DatasourceConfig}

object EncryptDataSourceUtils {

  def parseXml(xml: scala.xml.Node): DatasourceConfig = {
    val cfg = DataSourceUtils.parseXml(xml)

    val processor = PropertyProcessor.env()
    if (processor.changed) {
      cfg.user = processor.decrypt(cfg.user)
      cfg.password = processor.decrypt(cfg.password)
      cfg.props = cfg.props.map { case (k, v) =>
        (k, processor.decrypt(v))
      }
      cfg
    } else {
      cfg
    }

  }
}
