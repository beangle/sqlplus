[#ftl]
/**
 * @opt attributes
 * @hidden
 */
class UMLOptions {}
[#assign referTables=[]]

[#list image.tables as table]
/**
 * @note ${table.comment!}
[#list table.foreignKeys as fk]
 * @depend - - - ${fk.referencedTable.name?lower_case}
 [#if !image.matches(fk.referencedTable.name)]
 [#assign referTables=referTables + [fk.referencedTable]]
 [/#if]
[/#list]
 */
class ${table.name?lower_case}{
[#list table.foreignKeys as fk]
 public ${fk.referencedTable.name?lower_case}  [#list fk.columns as column]${column.name?lower_case}[/#list];
[/#list]
}
[/#list]


[#list referTables as table]
/**
[#if table.schema??]
	[#assign tableid=table.schema+"."+table.name]
[#else]
	[#assign tableid=table.name]
[/#if]
 * [#if (database.getTable(tableid)?exists)]
 * @note ${database.getTable(tableid).comment!}
 * [/#if]
 */
class ${table.name?lower_case}{}
[/#list]