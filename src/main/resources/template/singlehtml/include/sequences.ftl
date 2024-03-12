[#ftl]
数据库共计${allSequences?size}个序列，分别如下:

<table class="table table-bordered table-striped table-condensed">
  <tr>
    <th  class="info_header">序号</th>
    <th  class="info_header">序列名</th>
    <th  class="info_header">序号</th>
    <th  class="info_header">序列名</th>
  </tr>
  [#if allSequences?size>0]
  [#assign seqcnt = (allSequences?size/2)?int]
  [#if allSequences?size%2>0][#assign seqcnt = seqcnt+1][/#if]
  [#assign sortedSeqs = allSequences?sort_by("name")/]
  [#list 1..seqcnt as i]
  <tr>
    [#assign seq= sortedSeqs[i-1] /]
    <td>${i}</td>
    <td>${seq.name.value?lower_case}<br>${seq.comment!}</td>
    [#if allSequences[i-1+seqcnt]??]
    [#assign seq= sortedSeqs[i-1+seqcnt] /]
    <td>${i+seqcnt}</td>
    <td>${seq.name.value?lower_case}<br>${seq.comment!}</td>
    [#else]
    <td></td>
    <td></td>
    [/#if]
  </tr>
  [/#list]
  [/#if]
</table>
