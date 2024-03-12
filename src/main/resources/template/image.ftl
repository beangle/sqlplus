[#ftl]
@startuml
[#if image.direction??]
${image.direction} direction
[/#if]
title ${image.title}
[#assign referTables=[]]

[#list image.tables as table]
entity [@entityName table/]{
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

[#list image.tables as table]
  [#list table.foreignKeys as fk]
    [#if image.contains(fk.referencedTable.name)]
   ${table.name.value?lower_case} }o--  [@entityName fk.referencedTable/]
    [/#if]
  [/#list]
[/#list]
@enduml

[#macro entityName table][#if module.schema.name == table.schema.name.value]${table.name.value?lower_case}[#else]"${table.schema.name?lower_case}.${table.name.value?lower_case}"[/#if][/#macro]
