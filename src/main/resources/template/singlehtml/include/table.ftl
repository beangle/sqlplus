[#ftl]
[#macro drawtable table]
<div class="card card-info">
  <div class="card-header"><h5 id="table_${table.qualifiedName?lower_case}">表格${table.qualifiedName?lower_case} ${table.comment!}</h5></div>
  <div class="card-body">
    <ul>
      <li>表格中的列</li>
    </ul>
    <table class="table table-bordered table-striped table-condensed">
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

    [#if table.uniqueKeys?size>0 || table.primaryKey??]
    <ul>
      <li>表格中唯一约束</li>
    </ul>
    <table class="table table-bordered table-striped table-condensed">
      <thead>
        <tr>
          <th class="info_header index_td">序号</th>[#t/]
          <th class="info_header">约束名</th>[#t/]
          <th class="info_header">约束字段</th>[#t/]
        </tr>
      </thead>
      [#assign primaryKeySize=0/]

      [#if table.primaryKey??]
      [#assign primaryKeySize=1/]
      <tr>[#t/]
        <td class="index_td">1</td>[#t/]
        <td>${table.primaryKey.name.value?lower_case}</td>[#t/]
        <td>[#list table.primaryKey.columns as c]${c.value?lower_case}[#if c_has_next],[/#if][/#list]</td>[#t/]
      </tr>
      [/#if]

      [#list table.uniqueKeys as uk]
      <tr>[#t/]
        <td class="index_td">${uk_index+1+primaryKeySize}</td>[#t/]
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
      <thead>
        <tr>
          <th class="info_header index_td">序号</th>[#t/]
          <th class="info_header">索引名</th>[#t/]
          <th class="info_header">索引字段</th>[#t/]
          <th class="info_header">是否唯一</th>[#t/]
        </tr>
      </thead>
      [#list table.indexes as idx]
      <tr>[#t/]
        <td class="index_td">${idx_index+1}</td>[#t/]
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
