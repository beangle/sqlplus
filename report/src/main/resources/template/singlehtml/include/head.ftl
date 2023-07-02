<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8">
    <title>${report.system.name}${report.title}</title>
    <meta name="description" content="${report.system.name}${report.title}">
    <meta name="author" content="${(report.system.properties['vendor'])!}">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-9ndCyUaIbzAi2FUVXJi0CjmCapSmO7SnpJef0486qhLnuZ2cdeRhO02iuK6FUUVM" crossorigin="anonymous">
  </head>
  <body style="font-size:14px">
  <style>
    .h3, h3 {
        font-size: 24px;
    }
    .h4, h4 {
        font-size: 18px;
    }
    .h5, h5 {
        font-size: 14px;
    }
    .h4, .h5, .h6, h4, h5, h6 {
        margin-top: 10px;
        margin-bottom: 10px;
    }
   .info_header{
        color: #8a6d3b !important;
        background-color: #fcf8e3 !important;
        border-color: #faebcc !important;
     }
    .card-info > .card-header {
        color: #31708f !important;
        background-color: #d9edf7 !important;
        border-color: #bce8f1 !important;
    }
    body{
        line-height:20px;
    }
    .table-condensed > tbody > tr > td, .table-condensed > tbody > tr > th, .table-condensed > tfoot > tr > td, .table-condensed > tfoot > tr > th, .table-condensed > thead > tr > td, .table-condensed > thead > tr > th {
        padding: 5px;
    }
    .card{
        margin-bottom: 20px;
        border-color: #bce8f1 !important;
    }
  </style>
