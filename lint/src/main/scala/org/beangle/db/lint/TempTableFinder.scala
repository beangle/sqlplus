package org.beangle.db.lint

import org.beangle.commons.collection.Collections
import org.beangle.data.jdbc.meta.Schema.NameFilter
import org.beangle.data.jdbc.meta.{Database, Table}

object TempTableFinder {

  def find(database: Database, pattern: String): Seq[String] = {
    val filter = NameFilter(pattern)
    val tmpList = Collections.newBuffer[String]
    database.schemas.values.foreach { schema =>
      tmpList ++= filter.filter(schema.tables.values.map(x => x.name)).map(Table.qualify(schema, _))
    }
    tmpList.sorted.toSeq
  }
}
