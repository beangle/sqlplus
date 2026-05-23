[#ftl]
[#include "include/head.ftl"/]
[#include "include/table.ftl"/]

[#assign allImages = report.allImages/]
[#assign allSequences= report.allSequences/]
[#assign idx=0/]
<div class="container">
  <div class="page-header">
    <h1>${report.system.name} ${report.system.version!} ${report.title} </h1>
  </div>
  <div class="span12">
    <h4>目 录</h4>
    <ul>
    [#list report.schemas as s]
    [#list s.modules as m]
    [#if m.tables?size>0]
    <li> <a href="#${m.id}">${s_index+1}.${(m_index+1)?string} ${(m.schema.title)!} ${m.title}</a></li>
    [/#if]
    [/#list]
    [/#list]
    </ul>

    [#list report.schemas as s]
    [#list s.modules as module]
      [#assign idx=1/]
      <h3 id="${module.id}">${s_index+1}.${(module_index+1)?string}  ${(module.schema.title)!} ${module.title}</h3>
      <h4>${s_index+1}.${(module_index+1)?string}.${idx} 表格列表</h4>
      [#include "include/tables.ftl"/]
      [#if module.images?size>0]
        [#assign idx=idx+1/]
        <h4>${s_index+1}.${(module_index+1)?string}.${idx} 关系图</h4>
        [#list module.images as img]
        <h5>${img_index+1}. ${img.title}</h4>
        <p style="text-align:center"><img src="${report.imageurl}${img.name}.png" alt="${img.title}" /></p>
        [#if img.description?? && img.description?length>0]
        <ul><li>说明</li></ul>
        <p>${img.description}</p>
        [/#if]
        [/#list]
      [/#if]
      [#assign idx=idx+1/]
      <h4>${s_index+1}.${(module_index+1)?string}.${idx} 表格明细</h4>
      [#list module.tables?sort_by("name") as table]
      <h5 id="table_${table.qualifiedName?lower_case}">${s_index+1}.${(module_index+1)?string}.${idx}.${table_index+1} 表格${table.qualifiedName?lower_case} ${table.comment!}</h5>
      [@drawtable table/]
      [/#list]
    [/#list]
    [/#list]
  </div>
</div>
</body>
</html>
