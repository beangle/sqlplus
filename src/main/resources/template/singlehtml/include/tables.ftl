[#ftl]
[#list report.schemas as schema]
  [#list schema.modules as module]
    [#list module.groups as group]
    [#assign tables = group.tables/]
${schema.title}中的${module.title}-${group.title}下共计${tables?size}个表(${schema.name}.)，分别如下:

<table class="table table-bordered table-striped table-condensed">
  <tr>
    <th class="info_header" width="7%">序号</th>
    <th class="info_header" width="43%">表名/描述</th>
    <th class="info_header" width="7%">序号</th>
    <th class="info_header" width="43%">表名/描述</th>
  </tr>
  [#if tables?size>0]
  [#assign tabcnt = (tables?size/2)?int]
  [#if tables?size%2>0][#assign tabcnt = tabcnt+1][/#if]
  [#assign sortedTables = tables?sort_by("name")/]
  [#list 1..tabcnt as i]
  <tr>
    [#assign table= sortedTables[i-1] /]
    <td>${i}</td>
    <td><a href="#table_${table.qualifiedName?lower_case}">${table.name.value?lower_case}</a>&nbsp;${table.comment!}</td>
    [#if tables[i-1+tabcnt]??]
    [#assign table= sortedTables[i-1+tabcnt] /]
    <td>${i+tabcnt}</td>
    <td><a href="#table_${table.qualifiedName?lower_case}">${table.name.value?lower_case}</a>&nbsp;${table.comment!}</td>
    [#else]
    <td></td>
    <td></td>
    [/#if]
  </tr>
  [/#list]
  [/#if]
</table>

[/#list]
[/#list]
[/#list]
