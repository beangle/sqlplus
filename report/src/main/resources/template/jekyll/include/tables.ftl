[#ftl]
[#assign tables = report.tables/]
数据库共计${tables?size}个表，分别如下:

<table class="table table-bordered table-striped table-condensed">
  <tr>
    <th style="background-color:#D0D3FF">序号</th>
    <th style="background-color:#D0D3FF">表名/描述</th>
    <th style="background-color:#D0D3FF">序号</th>
    <th style="background-color:#D0D3FF">表名/描述</th>
  </tr>
  [#if tables?size>0]
  [#assign tabcnt = (tables?size/2)?int]
  [#if tables?size%2>0][#assign tabcnt = tabcnt+1][/#if]
  [#assign sortedTables = tables?sort_by("name")/]
  [#list 1..tabcnt as i]
  <tr>
  	[#assign table= sortedTables[i-1] /]
    <td>${i}</td>
    <td><a href="${report.findModule(table).path!"error"}.html#${table.name?lower_case?replace('_','')}">${table.name?lower_case}</a> ${table.comment!}</td>
    [#if tables[i-1+tabcnt]??]
    [#assign table= sortedTables[i-1+tabcnt] /]
    <td>${i+tabcnt}</td>
    <td><a href="${report.findModule(table).path!"error"}.html#${table.name?lower_case?replace('_','')}">${table.name?lower_case}</a> ${table.comment!}</td>
    [#else]
    <td></td>
    <td></td>
    [/#if]
  </tr>
  [/#list]
  [/#if]
</table>
