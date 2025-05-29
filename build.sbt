import org.beangle.parent.Dependencies.{mysql_connector_java, *}
import org.beangle.parent.Settings.*
import sbt.Keys.*

ThisBuild / organization := "org.beangle.sqlplus"
ThisBuild / version := "0.0.44-SNAPSHOT"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/beangle/sqlplus"),
    "scm:git@github.com:beangle/sqlplus.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id = "chaostone",
    name = "Tihua Duan",
    email = "duantihua@gmail.com",
    url = url("http://github.com/duantihua")
  )
)

ThisBuild / description := "The Beangle DB Utility"
ThisBuild / homepage := Some(url("https://beangle.github.io/sqlplus/index.html"))

val beangle_commons = "org.beangle.commons" % "beangle-commons" % "5.6.28"
val beangle_template = "org.beangle.template" % "beangle-template" % "0.1.26"
val beangle_jdbc = "org.beangle.jdbc" % "beangle-jdbc" % "1.0.11"

val commonDeps = Seq(beangle_commons, beangle_jdbc, beangle_template, logback_classic, logback_core,
  scalatest, HikariCP, plantuml, freemarker,
  postgresql, h2, jtds, ojdbc11, orai18n, mysql_connector_java, mssql_jdbc)

lazy val root = (project in file("."))
  .settings(
    name := "beangle-sqlplus",
    common,
    Compile / mainClass := Some("org.beangle.sqlplus.shell.Main"),
    libraryDependencies ++= commonDeps
  )

