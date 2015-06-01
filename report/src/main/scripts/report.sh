#!/bin/sh
PRG="$0"
PRGDIR=`dirname "$PRG"`
if [ "$1" = "" ] ; then
   echo "Ussage:report.sh /path/to/your/report.xml"
else
   java -cp "$PRGDIR/../lib/*" org.beangle.data.report.Reporter "$1"
fi