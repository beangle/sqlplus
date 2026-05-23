[#ftl]


# ${module.schema.title} ${module.title} 表结构

## 表格一览

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
    [#if module.tables?size>0]
    [#assign tables = module.tables/]
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

[#include "include/table.ftl"/]
[#if module.images?size>0]
## 关键关系图
[#list module.images as img]

### 关系图 ${img_index+1}. ${img.title}
  * 关系图

![${img.title}](${report.imageurl}${img.name}.png)

[#if img.description?? && img.description?length>0]
  * 说明

  ${img.description}
[/#if]
[/#list]
[/#if]

## 表格明细
[#list module.tables?sort_by("name") as table]

## ${table.name.value?lower_case}

[@drawtable table/]
[/#list]
