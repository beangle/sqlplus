package org.beangle.db.report.model

import org.beangle.commons.collection.Collections

import scala.collection.mutable

class Schema(val name: String, val title: String, val report: Report) {

  var modules: mutable.Buffer[Module] = Collections.newBuffer[Module]
}
