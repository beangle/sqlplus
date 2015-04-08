[#ftl]
[#include "include/head.ftl"/]
[@head title=module.title toc=true/]

[#include "include/table.ftl"/]
[#list module.images as img]

### 关系图 ${img_index+1}. ${img.title}
  * 关系图
  
![${img.title}](${report.imageurl}${img.name}.png)

[#if img.description?? && img.description?length>0]
  * 说明
  
  ${img.description}
[/#if]
[/#list]

[#list module.tables?sort_by("name") as table]

### 表格 ${table.name?lower_case}
[@drawtable table/]
[/#list]
