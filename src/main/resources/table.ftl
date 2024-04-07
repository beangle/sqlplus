  ${("table " + table.qualifiedName)?left_pad(40)} ${table.comment!}
[#assign max_column_span=1/]
[#list table.columns as column]
  [#if column.name?length gt max_column_span]
  [#assign max_column_span=column.name?length/]
  [/#if]
[/#list]
${"column"?right_pad(max_column_span)} | ${"type"?right_pad(15)} | ${"nullable"?right_pad(8)} | ${"default value"?right_pad(18)} | comment
[#list 1..max_column_span as i]-[/#list] + [#list 1..15 as i]-[/#list] + [#list 1..8 as i]-[/#list] + [#list 1..18 as i]-[/#list] + [#list 1..15 as i]-[/#list]
[#list table.columns as column]
${column.name?right_pad(max_column_span)} | ${column.sqlType.name?right_pad(15)} | ${column.nullable?string("","not null")?right_pad(8)} | ${(column.defaultValue!"")?right_pad(18)} | ${column.comment!}
[/#list]
[#if table.primaryKey??]
primary key:${table.primaryKey.name}([#list table.primaryKey.columns as column]${column!}[#sep],[/#list])
[/#if]
[#if table.uniqueKeys?size gt 0]
constraints:
[#list table.uniqueKeys as uniqueKey]
  ${uniqueKey.name}([#list uniqueKey.columns as column]${column}[#sep],[/#list]) unique
[/#list]
[#list table.foreignKeys as foreignKey]
  ${foreignKey.name}([#list foreignKey.columns as column]${column}[#sep],[/#list]) references ${foreignKey.referencedTable.name}([#list foreignKey.referencedColumns as column]${column}[#sep],[/#list])
[/#list]
[/#if]
[#if table.indexes?size gt 0]
indices:
[#list table.indexes as index]
  ${index.name}${index.unique?string(" unique ","")}([#list index.columns as column]${column}[#sep],[/#list])
[/#list]
[/#if]
