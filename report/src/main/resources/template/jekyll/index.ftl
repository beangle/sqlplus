[#ftl]
[#include "include/head.ftl"/]
[@head/]
[#include "include/module.ftl"/]

#### 目 录

##### 1. 数据库对象列表
  * [1.1 表格一览](index.html#表格一览)
[#if sequences?size>0]
  * [1.2 序列一览](sequences.html)
[/#if]
[#if report.images?size>0]
  * [[#if sequences?size>0]1.3[#else]1.2[/#if] 模块关系图](index.html#模块关系图)
[/#if]

##### 2. 具体模块明细
[#assign idx=1/]
[#list report.modules as m]
[#if m.tables?size>0]
  [@moduleindex "2."+idx,m;prefix,module/]
  [#assign idx=idx+1/]
[/#if]
[/#list]


### 表格一览
[#include "include/tables.ftl"/]

[#if report.images?size>0]
### 模块关系图

[#list report.images as img]

#### ${img_index+1}. ${img.title}
  * 关系图

![${img.title}](${report.imageurl}${img.name}.png)

[#if img.description?? && img.description?length>0]
  * 说明

  ${img.description}
[/#if]
[/#list]

[/#if]
