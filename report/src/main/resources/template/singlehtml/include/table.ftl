[#ftl]
[#macro drawtable table]
<div class="panel panel-info">
  <div class="panel-heading"><h5 id="table_${table.qualifiedName?lower_case}">表格${table.qualifiedName?lower_case}</h5></div>
  <div class="panel-body">
<ul>
  <li>表格说明</li>
</ul>

<table class="table table-bordered table-striped table-condensed ">
  <tr>[#t/]
    <th class="info_header">表名</th>[#t/]
    <th class="info_header">主键</th>[#t/]
    <th class="info_header">注释</th>[#t/]
  </tr>
  <tr>[#t/]
    <td>${table.qualifiedName?lower_case}</td>[#t/]
    <td>[#if table.primaryKey??][#list table.primaryKey.columns as c]${c.value?lower_case}[#if c_has_next],[/#if][/#list][/#if]</td>[#t/]
    <td>${table.comment!}</td>[#t/]
  </tr>
</table>
<ul>
  <li>表格中的列</li>
</ul>
<table class="table table-bordered table-striped table-condensed">
  <tr>[#t/]
    <th class="info_header">序号</th>[#t/]
    <th class="info_header">字段名</th>[#t/]
    <th class="info_header">字段类型</th>[#t/]
    <th class="info_header">是否可空</th>[#t/]
    <th class="info_header">描述</th>[#t/]
    <th class="info_header">引用表</th>[#t/]
  </tr>
  [#list table.columns as col]
  <tr>[#t/]
    <td>${col_index+1}</td>[#t/]
    <td>${col.name.value?lower_case}</td>[#t/]
    <td>${col.sqlType.name?lower_case}</td>[#t/]
    <td>${col.nullable?string("是","否")}</td>[#t/]
    <td>${col.comment!}</td>[#t/]
    <td>[#assign finded=false][#t/]
        [#list table.foreignKeys as fk]
        [#if !finded]
        [#list fk.columns as fcol]
          [#if fcol.value==col.name]
          <a href="#table_${fk.referencedTable.qualifiedName?lower_case}">${fk.referencedTable.qualifiedName?lower_case}</a>[#assign finded=true][#break/]
          [/#if]
        [/#list]
        [/#if]
        [/#list]
    </td>[#t/]
  </tr>
  [/#list]
</table>

[#if table.uniqueKeys?size>0]
<ul>
  <li>表格中唯一约束</li>
</ul>
<table class="table table-bordered table-striped table-condensed">
  <tr>
    <th class="info_header">序号</th>[#t/]
    <th class="info_header">约束名</th>[#t/]
    <th class="info_header">约束字段</th>[#t/]
  </tr>
  [#list table.uniqueKeys as uk]
  <tr>[#t/]
    <td>${uk_index+1}</td>[#t/]
    <td>${uk.name.value?lower_case}</td>[#t/]
    <td>[#list uk.columns as c]${c.value?lower_case}[#if c_has_next],[/#if][/#list]</td>[#t/]
  </tr>
  [/#list]
</table>
[/#if]

[#if table.indexes?size>0]
<ul>
  <li>表格的索引</li>
</ul>
<table class="table table-bordered table-striped table-condensed">
  <tr>
    <th class="info_header">索引名</th>[#t/]
    <th class="info_header">索引字段</th>[#t/]
    <th class="info_header">是否唯一</th>[#t/]
  </tr>
  [#list table.indexes as idx]
  <tr>[#t/]
    <td>${idx.name.value?lower_case}</td>[#t/]
    <td>[#list idx.columns as c]${c.value?lower_case}[#if c_has_next],[/#if][/#list]</td>[#t/]
    <td>${idx.unique?string("是","否")}</td>[#t/]
  </tr>
  [/#list]
</table>
[/#if]
  </div>
</div>
[/#macro]
