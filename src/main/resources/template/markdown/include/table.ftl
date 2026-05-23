[#ftl]
[#macro drawtable table]
<table class="table-entity">
  <tbody>
    <tr>
      <td class="table-entity-title" width="15%">表名:&nbsp;</td>
      <td>${table.qualifiedName?lower_case} ${table.comment!}</td>
    </tr>
    [#if table.uniqueKeys?size>0 || table.primaryKey??]
    <tr>
      <td class="table-entity-title">唯一约束:&nbsp;</td>
      <td>[#if table.primaryKey??]主键🔑([#list table.primaryKey.columns as c]${c.value?lower_case}[#if c_has_next],[/#if][/#list])[/#if] [#list table.uniqueKeys as uk]${uk.name.value?lower_case}([#list uk.columns as c]${c.value?lower_case}[#if c_has_next],[/#if][/#list])[/#list]</td>
    </tr>
    [/#if]
    [#if table.indexes?size>0]
    <tr>
      <td class="table-entity-title">索引:&nbsp;</td>
      <td>[#list table.indexes as idx]${idx.name.value?lower_case}([#list idx.columns as c]${c.value?lower_case}[#if c_has_next],[/#if][/#list]) ${idx.unique?string("唯一","")}[#sep],[/#list]</td>
    </tr>
    [/#if]
  </tbody>
</table>

<table class="table-entity">
  <thead>
    <tr>
      <th class="info_header text-center" width="7%">序号</th>[#t/]
      <th class="info_header" width="20%">字段名</th>[#t/]
      <th class="info_header" width="20%">字段类型</th>[#t/]
      <th class="info_header text-center" width="8%">是否可空</th>[#t/]
      <th class="info_header" width="25%">描述</th>[#t/]
      <th class="info_header" width="20%">引用表</th>[#t/]
    </tr>
  </thead>
  <tbody>
    [#list table.columns as col]
    <tr>
      <td class="text-center">${col_index+1}</td>
      <td>${col.name.value?lower_case}</td>
      <td>${col.sqlType.name?lower_case}</td>
      <td class="text-center">${col.nullable?string("是","否")}</td>
      <td>${col.comment!}</td>
      <td>[#rt/]
      [#assign finded=false][#t/]
          [#list table.foreignKeys as fk]
          [#if !finded]
          [#list fk.columns as fcol]
            [#if fcol.value==col.name]
              [#assign fkt=fk.referencedTable/]
              <a href="${report.refTableUrl(fkt)}">${fkt.qualifiedName?lower_case?lower_case}</a>[#t/]
              [#assign finded=true][#break/]
            [/#if][#t/]
          [/#list][#t/]
          [/#if][#t/]
          [/#list]
      </td>
    </tr>
    [/#list][#t/]
  </tbody>
</table>
[/#macro]
