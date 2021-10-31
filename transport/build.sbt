Compile / packageBin / mainClass := Some("org.beangle.db.transport.Reactor")
Compile / compile := (Compile / compile).dependsOn(BootPlugin.generateDependenciesTask).value
