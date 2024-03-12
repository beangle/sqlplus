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
  <body>
  <style>
    :root{
      font-size:14px;
    }
    .h1,.h2,.h3,.h4,.h5,.h6,h1,h2,h3,h4,h5,h6 {
      font-family:inherit;
      font-weight:500;
      line-height:1.1;
      color:inherit
    }
    .h1, .h2, .h3, h1, h2, h3 {
      margin-top: 20px;
      margin-bottom: 10px;
    }
    .h4, .h5, .h6, h4, h5, h6 {
      margin-top: 10px;
      margin-bottom: 10px;
    }
    .info_header{
      color: #8a6d3b !important;
      background-color: #fcf8e3 !important;
      border-color: #faebcc !important;
      --bs-table-bg-type: 0 !important;
    }
    .card-info > .card-header {
      color: #31708f !important;
      background-color: #d9edf7 !important;
      border-color: #bce8f1 !important;
    }
    .table-condensed > tbody > tr > td, .table-condensed > tbody > tr > th, .table-condensed > tfoot > tr > td, .table-condensed > tfoot > tr > th, .table-condensed > thead > tr > td, .table-condensed > thead > tr > th {
      padding: 5px;
    }
    .card{
      margin-bottom: 20px;
      border-color: #bce8f1 !important;
    }
    .page-header {
      padding-bottom: 9px;
      margin: 40px 0 20px;
      border-bottom: 1px solid #eee;
    }
    a {
      color: #337ab7;
      text-decoration: none;
    }
    dl, ol, ul {
      margin-top: 0;
      margin-bottom: 0.5rem;
    }
    @media print{
      :root{
        font-size:12px;
      }
      .container{
        margin:0px;
        width:100%;
        max-width: 20000px;
      }
    }
  </style>
