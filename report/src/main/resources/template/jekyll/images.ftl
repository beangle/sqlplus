[#ftl]
[#include "include/head.ftl"/]
[@head title="模块关系图" toc=true/]

[#list report.images as img]

#### ${img_index+1}. ${img.title}
  * 关系图
  
![${img.title}](${report.imageurl}${img.name}.png)

[#if img.description?? && img.description?length>0]
  * 说明
  
  ${img.description}
[/#if]
[/#list]