[#ftl]
[#macro grouptree prefix group]
<li> <a href="#${group.id}">${prefix} ${group.title}</a></li>
[#list group.children as m]
  [@grouptree prefix+"."+(m_index+1),m;prefix,group/]
[/#list]
[/#macro]

[#macro grouptables prefix group]
<h4 id="${group.id}">${prefix} ${group.title}</h4>
[#list group.tables?sort_by("name") as table]
[@drawtable table/]
[/#list]

[#list group.children as m]
  [@grouptables prefix+"."+(m_index+1),m;prefix,group/]
[/#list]
[/#macro]
