<?xml version="1.0" encoding="utf-8"?>
<report title="数据库设计说明">
  <system name="某系统" version="未知版本"/>
  <database xml="${database_file}"/>
  <pages template="singlehtml" extension=".html">
    <page name="index"/>
  </pages>
  <schemas>
    [#list database.schemas?keys?sort as schema]
    <schema name="${schema}" title="${schema}">
      <module title="所有表">
        <group name="all" title="所有表" tables="*"/>
      </module>
    </schema>
    [/#list]
  </schemas>
</report>
