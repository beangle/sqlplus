Compile / packageBin / packageOptions +=
  Package.ManifestAttributes(java.util.jar.Attributes.Name.MAIN_CLASS -> "org.beangle.db.transport.Reactor")

Compile / compile := (Compile / compile).dependsOn(BootPlugin.generateDependenciesTask).value
