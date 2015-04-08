[#ftl]
数据库共计${sequences?size}个序列，分别如下:

<table class="table table-bordered table-striped table-condensed">
  <tr>
    <th style="background-color:#D0D3FF">序号</th>
    <th style="background-color:#D0D3FF">表名/描述</th>
    <th style="background-color:#D0D3FF">序号</th>
    <th style="background-color:#D0D3FF">表名/描述</th>
  </tr>
  [#if sequences?size>0]
  [#assign seqcnt = (sequences?size/2)?int]
  [#if sequences?size%2>0][#assign seqcnt = seqcnt+1][/#if]
  [#assign sortedSeqs = sequences?sort_by("name")/]
  [#list 1..seqcnt as i]
  <tr>
  	[#assign seq= sortedSeqs[i-1] /]
    <td>${i}</td>
    <td>${seq.name?lower_case}<br>${seq.comment!}</td>
    [#if sequences[i-1+seqcnt]??]
    [#assign seq= sortedSeqs[i-1+seqcnt] /]
    <td>${i+seqcnt}</td>
    <td>${seq.name?lower_case}<br>${seq.comment!}</td>
    [#else]
    <td></td>
    <td></td>
    [/#if]
  </tr>
  [/#list]
  [/#if]
</table>