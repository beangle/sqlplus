<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8">
    <title>${report.system.name}${report.title}</title>
    <meta name="description" content="${report.system.name}${report.title}">
    <meta name="author" content="${(report.system.properties['vendor'])!}">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="http://netdna.bootstrapcdn.com/bootstrap/2.2.2/css/bootstrap.min.css" rel="stylesheet">
  </head>
  <style>
  /* Custom container */
.container-narrow {
  margin: 0 auto;
  max-width: 700px; }

.container-narrow > hr {
  margin: 30px 0; }

.navbar .nav {
  float: right; }

/* posts index */
.post > h3.title {
  position: relative;
  padding-top: 10px; }

.post > h3.title span.date {
  position: absolute;
  right: 0;
  font-size: 0.9em; }

.post > .more {
  margin: 10px 0;
  text-align: left; }

/* post-full*/
.post-full .date {
  margin-bottom: 20px;
  font-weight: bold; }

/* tag_box */
.tag_box {
  list-style: none;
  margin: 0;
  overflow: hidden; }

.tag_box li {
  line-height: 28px; }

.tag_box li i {
  opacity: 0.9; }

.tag_box.inline li {
  float: left; }

.tag_box a {
  padding: 3px 6px;
  margin: 2px;
  background: #eee;
  color: #555;
  border-radius: 3px;
  text-decoration: none;
  border: 1px dashed #cccccc; }

.tag_box a span {
  vertical-align: super;
  font-size: 0.8em; }

.tag_box a:hover {
  background-color: #e5e5e5; }

.tag_box a.active {
  background: #57A957;
  border: 1px solid #4c964d;
  color: #FFF; }
</style>
  <body>