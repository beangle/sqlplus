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
import org.beangle.data.jdbc.dialect.Dialects

class Migrator {

  def sql(diff: DatabaseDiff): Iterable[String] = {
    if (diff.isEmpty) return List.empty

    val sb = Collections.newBuffer[String]
    val engine = diff.newer.engine
    val dialect = Dialects.forName(engine.name)

    diff.schemas.newer foreach { n =>
      sb += s"""create schema $n;"""
    }
    diff.schemas.removed foreach { n =>
      sb += s"DROP schema $n cascade;"
    }
    diff.schemaDiffs foreach { case (schema, sdf) =>
      sdf.tables.removed foreach { t =>
        sb += dialect.dropTable(diff.older.getTable(schema, t).get.qualifiedName)
      }
      sdf.tables.newer foreach { t =>
        sb += dialect.createTable(diff.newer.getTable(schema, t).get)
      }
      sdf.tableDiffs foreach { case (_, tdf) =>
        if(tdf.hasComment){
          sb ++= dialect.commentsOnTable(tdf.older.qualifiedName,tdf.newer.comment)
        }
        tdf.columns.removed foreach { c =>
          sb += dialect.alterTableDropColumn(tdf.older, tdf.older.column(c))
        }
        tdf.columns.newer foreach { c =>
          sb ++= dialect.alterTableAddColumn(tdf.newer, tdf.newer.column(c))
        }
        tdf.columns.updated foreach { c =>
          val nCol = tdf.newer.column(c)
          val oCol = tdf.older.column(c)
          if (nCol.sqlType != oCol.sqlType) {
            sb += dialect.alterTableModifyColumnType(tdf.older, oCol, nCol.sqlType)
          }
          if (nCol.defaultValue != oCol.defaultValue) {
            sb += dialect.alterTableModifyColumnDefault(tdf.older, oCol, nCol.defaultValue)
          }
          if (nCol.nullable != oCol.nullable) {
            if (nCol.nullable) {
              sb += dialect.alterTableModifyColumnDropNotNull(tdf.newer, nCol)
            } else {
              sb += dialect.alterTableModifyColumnSetNotNull(tdf.newer, nCol)
            }
          }
          if (nCol.comment != oCol.comment) {
            sb ++= dialect.commentsOnColumn(tdf.older, oCol, nCol.comment)
          }
          // ignore check and unique,using constrants
        }
        if (tdf.hasPrimaryKey) {
          if (tdf.older.primaryKey.nonEmpty) {
            sb += dialect.alterTableDropPrimaryKey(tdf.older, tdf.older.primaryKey.get)
          }
          if (tdf.newer.primaryKey.nonEmpty) {
            sb += dialect.alterTableAddPrimaryKey(tdf.newer, tdf.newer.primaryKey.get)
          }
        }

        // remove old forignkeys
        tdf.foreignKeys.removed foreach { fk =>
          sb += dialect.alterTableDropConstraint(tdf.older, fk)
        }
        tdf.foreignKeys.updated foreach { fk =>
          sb += dialect.alterTableDropConstraint(tdf.older, fk)
          sb += dialect.alterTableAddForeignKey(tdf.newer.getForeignKey(fk).get)
        }

        tdf.foreignKeys.newer foreach { fk =>
          sb += dialect.alterTableAddForeignKey(tdf.newer.getForeignKey(fk).get)
        }

        // remove old uniquekeys
        tdf.uniqueKeys.removed foreach { uk =>
          sb += dialect.alterTableDropConstraint(tdf.older, uk)
        }

        tdf.uniqueKeys.updated foreach { uk =>
          sb += dialect.alterTableDropConstraint(tdf.older, uk)
          sb += dialect.alterTableAddUnique(tdf.newer.getUniqueKey(uk).get)
        }
        tdf.uniqueKeys.newer foreach { uk =>
          sb += dialect.alterTableAddUnique(tdf.newer.getUniqueKey(uk).get)
        }

        //remove old index
        tdf.indexes.removed foreach { idx =>
          sb += dialect.dropIndex(tdf.older.getIndex(idx).get)
        }

        tdf.indexes.updated foreach { idx =>
          sb += dialect.dropIndex(tdf.older.getIndex(idx).get)
          sb += dialect.createIndex(tdf.newer.getIndex(idx).get)
        }

        tdf.indexes.newer foreach { idx =>
          sb += dialect.createIndex(tdf.newer.getIndex(idx).get)
        }
      }
    }
    sb
  }

}