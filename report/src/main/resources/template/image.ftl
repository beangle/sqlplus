[#ftl]
@startuml
title ${image.title}
[#assign referTables=[]]

[#list image.tables as table]
entity ${table.name.value?lower_case}{
 * id
 --
[#list table.foreignKeys as fk]
  [#list fk.columns as column]${column.value?lower_case}[#if column_has_next],[/#if][/#list]
  [#if !image.contains(fk.referencedTable.name) && !(referTables?seq_contains(fk.referencedTable))]
   [#assign referTables=referTables + [fk.referencedTable]]
  [/#if]
[/#list]
}
note "${table.comment!}" as ${table.name.value?lower_case}_comments
[@entityName table/] .. ${table.name.value?lower_case}_comments

[/#list]


[#list referTables as table]
entity [@entityName table/]  #line.dotted:blue
note "${schema.database.getTable(table.schema.name.value,table.name.value).comment!}" as ${table.name.value?lower_case}_comments
[@entityName table/] .. ${table.name.value?lower_case}_comments
hide [@entityName table/] members
[/#list]

[#list image.tables as table]
  [#list table.foreignKeys as fk]
   ${table.name.value?lower_case} }o--  [@entityName fk.referencedTable/]
  [/#list]
[/#list]
@enduml

[#macro entityName table][#if schema.name.value == table.schema.name.value]${table.name.value?lower_case}[#else]"${table.schema.name.value?lower_case}.${table.name.value?lower_case}"[/#if][/#macro]