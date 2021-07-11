[#ftl]
[#macro grouptree prefix group]
<li> <a href="#group${prefix}">${prefix} ${group.title}</a></li>
[#list group.children as m]
  [@grouptree prefix+"."+(m_index+1),m;prefix,group/]
[/#list]
[/#macro]

[#macro grouptables prefix group]
<h4 id="group${prefix}">${prefix} ${group.title}</h4>
[#list group.tables?sort_by("name") as table]
<h5 id="table_${table.name.value?lower_case}">表格${table.name.value?lower_case}</h5>
[@drawtable table/]
[/#list]

[#list group.children as m]
  [@grouptables prefix+"."+(m_index+1),m;prefix,group/]
[/#list]
[/#macro]
