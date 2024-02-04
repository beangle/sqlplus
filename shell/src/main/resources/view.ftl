 view ${view.qualifiedName?left_pad(30)}
[#assign max_column_span=1/]
[#list view.columns as column]
  [#if column.name?length gt max_column_span]
  [#assign max_column_span=column.name?length/]
  [/#if]
[/#list]
${"column"?right_pad(max_column_span)} | ${"type"?right_pad(15)} | ${"nullable"?right_pad(8)} | default value
[#list 1..max_column_span as i]-[/#list] + [#list 1..15 as i]-[/#list] + [#list 1..8 as i]-[/#list] + [#list 1..10 as i]-[/#list]
[#list view.columns as column]
${column.name?right_pad(max_column_span)} | ${column.sqlType.name?right_pad(15)} | ${column.nullable?string("not null","")?right_pad(8)} | ${column.defaultValue!}
[/#list]

[#if view.definition??]
definition:
${view.definition}
[/#if]
