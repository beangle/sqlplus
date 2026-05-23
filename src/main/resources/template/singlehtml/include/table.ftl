[#ftl]
[#macro drawtable table]
<table class="table table-sm table-entity">
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

<table class="table table-sm table-entity">
  <thead>
    <tr>[#t/]
      <th class="info_header index_td">序号</th>[#t/]
      <th class="info_header">字段名</th>[#t/]
      <th class="info_header">字段类型</th>[#t/]
      <th class="info_header">是否必须</th>[#t/]
      <th class="info_header">描述</th>[#t/]
      <th class="info_header">引用表</th>[#t/]
    </tr>
  </thead>
  [#list table.columns as col]
  <tr>[#t/]
    <td class="index_td">${col_index+1}</td>[#t/]
    <td>${col.name.value?lower_case}</td>[#t/]
    <td>${col.sqlType.name?lower_case}</td>[#t/]
    <td>${col.nullable?string("","&#10003;")}</td>[#t/]
    <td>${col.comment!}</td>[#t/]
    <td>[#assign finded=false][#t/]
        [#list table.foreignKeys as fk]
        [#if !finded]
        [#list fk.columns as fcol]
          [#if fcol.value==col.name]
            [#assign fkt=fk.referencedTable/]
            <a href="#table_${fk.referencedTable.qualifiedName?lower_case}">${fk.referencedTable.qualifiedName?lower_case}</a>[#assign finded=true][#break/]
          [/#if]
        [/#list]
        [/#if]
        [/#list]
    </td>[#t/]
  </tr>
  [/#list]
</table>
[/#macro]
