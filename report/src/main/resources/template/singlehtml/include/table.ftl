[#ftl]
[#macro drawtable table]
<ul>
  <li>表格说明</li>
</ul>

<table class="table table-bordered table-striped table-condensed">
  <tr>[#t/]
    <th style="background-color:#D0D3FF">表名</th>[#t/]
    <th style="background-color:#D0D3FF">主键</th>[#t/]
    <th style="background-color:#D0D3FF">注释</th>[#t/]
  </tr>
  <tr>[#t/]
    <td>${table.name?lower_case}</td>[#t/]
    <td>[#if table.primaryKey??][#list table.primaryKey.columns as c]${c.name?lower_case}[#if c_has_next],[/#if][/#list][/#if]</td>[#t/]
    <td>${table.comment!}</td>[#t/]
  </tr>
</table>
<ul>
  <li>表格中的列</li>
</ul>
<table class="table table-bordered table-striped table-condensed">
  <tr>[#t/]
    <th style="background-color:#D0D3FF">序号</th>[#t/]
    <th style="background-color:#D0D3FF">字段名</th>[#t/]
    <th style="background-color:#D0D3FF">字段类型</th>[#t/]
    <th style="background-color:#D0D3FF">是否可空</th>[#t/]
    <th style="background-color:#D0D3FF">描述</th>[#t/]
    <th style="background-color:#D0D3FF">引用表</th>[#t/]
  </tr>
  [#list table.columns as col]
  <tr>[#t/]
    <td>${col_index+1}</td>[#t/]
    <td>${col.name?lower_case}</td>[#t/]
    <td>${col.getSqlType(dialect)?lower_case}</td>[#t/]
    <td>${col.nullable?string("是","否")}</td>[#t/]
    <td>${col.comment!}</td>[#t/]
    <td>[#assign finded=false][#t/]
        [#list table.foreignKeys as fk]
        [#if !finded]
        [#list fk.columns as fcol][#if fcol.name==col.name]${fk.referencedTable.name?lower_case}[#assign finded=true][#break/][/#if][/#list][#t/]
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
    <th style="background-color:#D0D3FF">序号</th>[#t/]
    <th style="background-color:#D0D3FF">约束名</th>[#t/]
    <th style="background-color:#D0D3FF">约束字段</th>[#t/]
  </tr>
  [#list table.uniqueKeys as uk]
  <tr>[#t/]
    <td>${uk_index+1}</td>[#t/]
    <td>${uk.name?lower_case}</td>[#t/]
    <td>[#list uk.columns as c]${c.name?lower_case}&nbsp;[/#list]</td>[#t/]
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
    <th style="background-color:#D0D3FF">索引名</th>[#t/]
    <th style="background-color:#D0D3FF">索引字段</th>[#t/]
    <th style="background-color:#D0D3FF">是否唯一</th>[#t/]
  </tr>
  [#list table.indexes as idx]
  <tr>[#t/]
    <td>${idx.name?lower_case}</td>[#t/]
    <td>[#list idx.columns as c]${c.name?lower_case}&nbsp;[/#list]</td>[#t/]
    <td>${idx.unique?string("是","否")}</td>[#t/]
  </tr>
  [/#list]
</table>
[/#if]
[/#macro]