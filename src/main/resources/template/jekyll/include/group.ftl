[#ftl]
[#macro groupindex prefix group]
* [${prefix} ${group.title}](${report.contextPath}${group.path}.html)
[#list group.children as m]
  [@groupindex prefix+"."+(m_index+1),m;prefix,group/]
[/#list]
[/#macro]

[#macro grouptree prefix group]
* ${prefix} ${group.title}
[#list group.children as m]
  [@grouptree prefix+"."+(m_index+1),m;prefix,group/]
[/#list]
[/#macro]

[#macro grouptables prefix group]
#### ${prefix} ${group.title}
[#list group.tables?sort_by("name") as table]

##### 表${table.name.value?lower_case}  ${table.comment!}

[@drawtable table/]
[/#list]

[#list group.children as m]
  [@grouptables prefix+"."+(m_index+1),m;prefix,group/]
[/#list]
[/#macro]
