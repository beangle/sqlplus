[#ftl]
[#include "include/head.ftl"/]
[#include "include/table.ftl"/]
[#include "include/module.ftl"/]

<div class="container-narrow">
 <div class="content">
  <div class="page-header">
   <h1>${report.system.name} ${report.system.version!} ${report.title} </h1>
  </div>

  <div class="row-fluid">
   <div class="span12">

    <h4>目 录</h4>

    <h5>1. 数据库对象列表</h5>
    <ul>
      <li><a href="#table_list">1.1 表格一览</a></li>
      <li><a href="#sequence_list">1.2 序列一览</a></li>
      <li><a href="#image_list">1.3 模块关系图</a></li>
    </ul>

    <h5>2. 具体模块明细</h5>
    <ul>
    [#list report.modules as m]
    [@moduletree "2."+(m_index+1),m;prefix,module/]
    [/#list]
    </ul>

    <h3>2. 数据库对象列表</h3>

    <h4 id="table_list">2.1 表格列表</h4>

    [#include "include/tables.ftl"/]

    <h4 id="sequence_list">2.2 序列一览</h4>

    [#include "include/sequences.ftl"/]

    <h4 id="image_list">2.3 模块关系图</h4>
    [#list report.images as img]

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

    <h3>3. 具体模块明细</h3>

    [#list report.modules as m]
    [@moduletables "3."+(m_index+1),m;prefix,module/]
    [/#list]

   </div>
  </div>
 </div>
</div>
</body>
</html>