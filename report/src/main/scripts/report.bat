@echo off
if "%1" == "" goto help
java -cp "%cd%\lib\*" org.beangle.data.report.Reporter %1
goto end

:help
  echo Usage: report.bat path\to\your\report.xml

:end