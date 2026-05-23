[#ftl]
[#assign tables = module.tables/]
${module.schema.name}模式下[#if module.name??]${module.name}模块中[/#if]下共计${tables?size}个表，分别如下:

<table class="table-mini">
  <thead>
    <tr>
      <th class="info_header text-center" width="7%">序号</th>
      <th class="info_header" width="43%">表名/描述</th>
      <th class="info_header text-center" width="7%">序号</th>
      <th class="info_header" width="43%">表名/描述</th>
    </tr>
  </thead>
  <tbody>
    [#if tables?size>0]
    [#assign tabcnt = (tables?size/2)?int]
    [#if tables?size%2>0][#assign tabcnt = tabcnt+1][/#if]
    [#assign sortedTables = tables?sort_by("name")/]
    [#list 1..tabcnt as i]
    <tr>
      [#assign table= sortedTables[i-1] /]
      <td class="text-center">${i}</td>
      <td><a href="${report.tableUrl(table)}">${table.name.value?lower_case}</a> ${table.comment!}</td>
      [#if tables[i-1+tabcnt]??]
      [#assign table= sortedTables[i-1+tabcnt] /]
      <td class="text-center">${i+tabcnt}</td>
      <td><a href="${report.tableUrl(table)}">${table.name.value?lower_case}</a> ${table.comment!}</td>
      [#else]
      <td></td>
      <td></td>
      [/#if]
    </tr>
    [/#list]
    [/#if]
  </tbody>
</table>
