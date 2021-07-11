[#ftl]
[#macro head title="" toc=false]
---
layout: page
title: ${module.title} ${title}
description: "${module.title}${title}"
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
