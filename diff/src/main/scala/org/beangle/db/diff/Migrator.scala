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

class Migrator {

  def sql(diff: DatabaseDiff): Iterable[String] = {
    if (diff.isEmpty) return List.empty

    val sb = Collections.newBuffer[String]
    val engine = diff.newer.engine
    diff.schemas.newer foreach { n =>
      sb += s"""create schema $n;"""
    }
    diff.schemas.removed foreach { n =>
      sb += s"DROP schema $n cascade;"
    }
    diff.schemaDiffs foreach { case (schema, sdf) =>
      sdf.tables.removed foreach { t =>
        sb += engine.dropTable(diff.older.getTable(schema, t).get.qualifiedName)
      }
      sdf.tables.newer foreach { t =>
        sb += engine.createTable(diff.newer.getTable(schema, t).get)
      }
      sdf.tableDiffs foreach { case (_, tdf) =>
        if (tdf.hasComment) {
          sb ++= engine.commentsOnTable(tdf.older.qualifiedName, tdf.newer.comment)
        }
        tdf.columns.removed foreach { c =>
          sb += engine.alterTableDropColumn(tdf.older, tdf.older.column(c))
        }
        tdf.columns.newer foreach { c =>
          sb ++= engine.alterTableAddColumn(tdf.newer, tdf.newer.column(c))
        }
        tdf.columns.updated foreach { c =>
          val nCol = tdf.newer.column(c)
          val oCol = tdf.older.column(c)
          if (nCol.sqlType != oCol.sqlType) {
            sb += engine.alterTableModifyColumnType(tdf.older, oCol, nCol.sqlType)
          }
          if (nCol.defaultValue != oCol.defaultValue) {
            sb += engine.alterTableModifyColumnDefault(tdf.older, oCol, nCol.defaultValue)
          }
          if (nCol.nullable != oCol.nullable) {
            if (nCol.nullable) {
              sb += engine.alterTableModifyColumnDropNotNull(tdf.newer, nCol)
            } else {
              sb += engine.alterTableModifyColumnSetNotNull(tdf.newer, nCol)
            }
          }
          if (nCol.comment != oCol.comment) {
            sb ++= engine.commentsOnColumn(tdf.older, oCol, nCol.comment)
          }
          // ignore check and unique,using constrants
        }
        if (tdf.hasPrimaryKey) {
          if (tdf.older.primaryKey.nonEmpty) {
            sb += engine.alterTableDropPrimaryKey(tdf.older, tdf.older.primaryKey.get)
          }
          if (tdf.newer.primaryKey.nonEmpty) {
            sb += engine.alterTableAddPrimaryKey(tdf.newer, tdf.newer.primaryKey.get)
          }
        }

        // remove old forignkeys
        tdf.foreignKeys.removed foreach { fk =>
          sb += engine.alterTableDropConstraint(tdf.older, fk)
        }
        tdf.foreignKeys.updated foreach { fk =>
          sb += engine.alterTableDropConstraint(tdf.older, fk)
          sb += engine.alterTableAddForeignKey(tdf.newer.getForeignKey(fk).get)
        }

        tdf.foreignKeys.newer foreach { fk =>
          sb += engine.alterTableAddForeignKey(tdf.newer.getForeignKey(fk).get)
        }

        // remove old uniquekeys
        tdf.uniqueKeys.removed foreach { uk =>
          sb += engine.alterTableDropConstraint(tdf.older, uk)
        }

        tdf.uniqueKeys.updated foreach { uk =>
          sb += engine.alterTableDropConstraint(tdf.older, uk)
          sb += engine.alterTableAddUnique(tdf.newer.getUniqueKey(uk).get)
        }
        tdf.uniqueKeys.newer foreach { uk =>
          sb += engine.alterTableAddUnique(tdf.newer.getUniqueKey(uk).get)
        }

        //remove old index
        tdf.indexes.removed foreach { idx =>
          sb += engine.dropIndex(tdf.older.getIndex(idx).get)
        }

        tdf.indexes.updated foreach { idx =>
          sb += engine.dropIndex(tdf.older.getIndex(idx).get)
          sb += engine.createIndex(tdf.newer.getIndex(idx).get)
        }

        tdf.indexes.newer foreach { idx =>
          sb += engine.createIndex(tdf.newer.getIndex(idx).get)
        }
      }
    }
    sb
  }

}
