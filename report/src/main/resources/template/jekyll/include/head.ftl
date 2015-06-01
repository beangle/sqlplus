[#ftl]
[#macro head title="" toc=false]
---
layout: page
title: ${report.system.name} ${title}
description: "${report.system.name}${title}"
categories: [model-${report.system.version}]
version: ["${report.system.version}"]
---
{% include JB/setup %}
[#if toc]
 目  录

* toc
{:toc}
[/#if]
[/#macro]
