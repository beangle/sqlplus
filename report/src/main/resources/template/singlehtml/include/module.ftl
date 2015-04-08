[#ftl]
[#macro moduletree prefix module]
<li> <a href="#module${prefix}">${prefix} ${module.title}</a></li>
[#list module.children as m]
	[@moduletree prefix+"."+(m_index+1),m;prefix,module/]
[/#list]
[/#macro]

[#macro moduletables prefix module]
<h4 id="module${prefix}">${prefix} ${module.title}</h4>
[#list module.tables?sort_by("name") as table]
<h5 id="table_${table.name?lower_case}">表格${table.name?lower_case}</h5>
[@drawtable table/]
[/#list]

[#list module.children as m]
	[@moduletables prefix+"."+(m_index+1),m;prefix,module/]
[/#list]
[/#macro]
