Compile / packageBin / mainClass := Some("org.beangle.db.report.Reporter")
Compile / compile := (Compile / compile).dependsOn(BootPlugin.generateDependenciesTask).value
