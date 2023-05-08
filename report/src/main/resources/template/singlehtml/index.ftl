[#ftl]
[#include "include/head.ftl"/]
[#include "include/table.ftl"/]
[#include "include/group.ftl"/]

[#assign allImages = report.allImages/]
[#assign allSequences= report.allSequences/]
[#assign idx=0/]
<div class="container">
 <div class="content">
  <div class="page-header">
   <h1>${report.system.name} ${report.system.version!} ${report.title} </h1>
  </div>

  <div class="row-fluid">
   <div class="span12">

    <h4>目 录</h4>

    <h5>1. 数据库对象</h5>
    <ul>
      [#assign idx=2/]
      <li><a href="#table_list">1.1 表格一览</a></li>
      [#if allSequences?size >0 ]<li><a href="#sequence_list">1.${idx} [#assign idx=dix+1/] 序列一览</a></li>[/#if]
      [#if allImages?size >0]<li><a href="#image_list">1.${idx} [#assign idx=idx+1/] 模块关系图</a></li>[/#if]
    </ul>

    <h5>2. 模块列表</h5>
    <ul>
    [#list report.allGroups as m]
    [@grouptree "2."+(m_index+1),m;prefix,group/]
    [/#list]
    </ul>

    <h3>2. 数据库对象列表</h3>

    <h4 id="table_list">2.1 表格列表</h4>

    [#include "include/tables.ftl"/]

    [#if allSequences?size >0 ]
    <h4 id="sequence_list">2.2 序列一览</h4>

    [#include "include/sequences.ftl"/]
    [/#if]

   [#if allImages?size>0]
    <h4 id="image_list">[#if allSequences?size >0 ]2.3[#else]2.2[/#if] 模块关系图</h4>
    [#list allImages as img]

    <h4>${img_index+1}. ${img.title}</h4>
    <ul>
      <li>关系图</li>
    </ul>
    <p><img src="${report.imageurl}${img.name}.png" alt="${img.title}" /></p>
    [#if img.description?? && img.description?length>0]
    <ul>
      <li>说明</li>
    </ul>
    <p>${img.description}</p>
    [/#if]
    [/#list]
   [/#if]

    <h3>3. 具体模块明细</h3>

    [#list report.allGroups as m]
    [@grouptables "3."+(m_index+1),m;prefix,group/]
    [/#list]

   </div>
  </div>
 </div>
</div>
</body>
</html>
