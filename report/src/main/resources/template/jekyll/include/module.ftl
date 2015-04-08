[#ftl]
[#macro moduleindex prefix module]
* [${prefix} ${module.title}](${module.path}.html)
[#list module.children as m]
	[@moduleindex prefix+"."+(m_index+1),m;prefix,module/]
[/#list]
[/#macro]

[#macro moduletree prefix module]
* ${prefix} ${module.title}
[#list module.children as m]
	[@moduletree prefix+"."+(m_index+1),m;prefix,module/]
[/#list]
[/#macro]

[#macro moduletables prefix module]
#### ${prefix} ${module.title}
[#list module.tables?sort_by("name") as table]

##### 表格${table.name?lower_case}

[@drawtable table/]
[/#list]

[#list module.children as m]
	[@moduletables prefix+"."+(m_index+1),m;prefix,module/]
[/#list]
[/#macro]
