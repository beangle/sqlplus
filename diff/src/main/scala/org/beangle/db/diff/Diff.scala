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

import org.beangle.commons.collection.Collections
import org.beangle.data.jdbc.meta._

class Diff {

  def diff(newer: Database, older: Database): DatabaseDiff = {
    if (newer.engine != older.engine) {
      throw new RuntimeException(s"Cannot diff different engines(${newer.engine.name} and ${older.engine.name}).")
    }
    val newSchemaSet = newer.schemas.keySet.map(_.value)
    val oldSchemaSet = older.schemas.keySet.map(_.value)

    val newSchemas = newSchemaSet.diff(oldSchemaSet)
    val removedSchemas = oldSchemaSet.diff(newSchemaSet)
    val updateSchemas = newSchemaSet.intersect(oldSchemaSet)

    val schemaDiffs = Collections.newMap[String, SchemaDiff]
    updateSchemas foreach { s =>
      val newSchema = newer.getOrCreateSchema(s)
      val oldSchema = older.getOrCreateSchema(s)

      val newTableSet = newSchema.tables.keySet.map(_.value)
      val oldTableSet = oldSchema.tables.keySet.map(_.value)
      val newTables = newTableSet.diff(oldTableSet)
      val removedTables = oldTableSet.diff(newTableSet)
      val updateTables = newTableSet.intersect(oldTableSet)
      val tableDiffs = Collections.newMap[String, TableDiff]
      updateTables foreach { t =>
        val newT = newSchema.getTable(t).orNull
        val oldT = oldSchema.getTable(t).orNull
        diff(newT, oldT) foreach { td =>
          tableDiffs.put(t, td)
        }
      }

      if (!(newTables.isEmpty && removedTables.isEmpty && tableDiffs.isEmpty)) {
        val schemaDiff = new SchemaDiff(newSchema, oldSchema)
        schemaDiff.tableDiffs = tableDiffs.toMap
        schemaDiff.tables = NameDiff(newTables, removedTables, tableDiffs.keySet)
        schemaDiffs.put(s, schemaDiff)
      }
    }
    val dbDiff = new DatabaseDiff(newer, older)
    if (!(newSchemas.isEmpty && removedSchemas.isEmpty && schemaDiffs.isEmpty)) {
      dbDiff.schemas = NameDiff(newSchemas, removedSchemas, schemaDiffs.keySet)
      dbDiff.schemaDiffs = schemaDiffs.toMap
    }
    dbDiff
  }

  protected[migration] def diff(newer: Table, older: Table): Option[TableDiff] = {
    val table = new TableDiff(newer, older)
    if (newer.primaryKey != older.primaryKey) {
      table.hasPrimaryKey = true
    }
    if (newer.comment != older.comment) {
      table.hasComment = true
    }
    val newColMap = newer.columns.map(c => (c.name.value, c)).toMap
    val oldColMap = older.columns.map(c => (c.name.value, c)).toMap
    table.columns = diff(newColMap.keySet, oldColMap.keySet, newColMap, oldColMap)

    val newUkMap = newer.uniqueKeys.map(c => (c.name.value, c)).toMap
    val oldUkMap = older.uniqueKeys.map(c => (c.name.value, c)).toMap
    table.uniqueKeys = diff(newUkMap.keySet, oldUkMap.keySet, newUkMap, oldUkMap)

    val newFkMap = newer.foreignKeys.map(c => (c.name.value, c)).toMap
    val oldFkMap = older.foreignKeys.map(c => (c.name.value, c)).toMap
    table.foreignKeys = diff(newFkMap.keySet, oldFkMap.keySet, newFkMap, oldFkMap)

    val newIdxMap = newer.indexes.map(c => (c.name.value, c)).toMap
    val oldIdxMap = older.indexes.map(c => (c.name.value, c)).toMap
    table.indexes = diff(newIdxMap.keySet, oldIdxMap.keySet, newIdxMap, oldIdxMap)

    if (table.isEmpty) None else Some(table)
  }

  private def diff(names1: Set[String], names2: Set[String],
                   data1: collection.Map[String, Any],
                   data2: collection.Map[String, Any]): NameDiff = {
    val updated = names2.intersect(names1) filter (n => data1(n) != data2(n))
    NameDiff(names1.diff(names2), names2.diff(names1), updated)
  }
}

class DatabaseDiff(val newer: Database, val older: Database) {
  var schemas: NameDiff = NameDiff(Set.empty, Set.empty, Set.empty)
  var schemaDiffs: Map[String, SchemaDiff] = Map.empty

  def isEmpty: Boolean = {
    (schemaDiffs == null || schemaDiffs.isEmpty) && (schemas == null || schemas.isEmpty)
  }
}

class SchemaDiff(val newer: Schema, val older: Schema) {
  var tables: NameDiff = _
  var tableDiffs: Map[String, TableDiff] = _
}

case class NameDiff(newer: collection.Set[String], removed: collection.Set[String], updated: collection.Set[String]) {
  def isEmpty: Boolean = {
    newer.isEmpty && removed.isEmpty && updated.isEmpty
  }
}

class TableDiff(val newer: Table, val older: Table) {
  var hasPrimaryKey: Boolean = _
  var hasComment: Boolean = _
  var columns: NameDiff = _
  var uniqueKeys: NameDiff = _
  var foreignKeys: NameDiff = _
  var indexes: NameDiff = _

  def isEmpty: Boolean = {
    !hasPrimaryKey && !hasComment && columns.isEmpty && uniqueKeys.isEmpty && foreignKeys.isEmpty && indexes.isEmpty
  }
}