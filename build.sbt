import org.beangle.parent.Dependencies.*
import org.beangle.parent.Settings.*
import sbt.Keys.*

organization := "org.beangle.sqlplus"
version := "0.2.7-SNAPSHOT"
scmInfo := Some(
  ScmInfo(
    uri("https://github.com/beangle/sqlplus"),
    "scm:git@github.com:beangle/sqlplus.git"
  )
)

developers := List(
  Developer(
    id = "chaostone",
    name = "Tihua Duan",
    email = "duantihua@gmail.com",
    url = uri("http://github.com/duantihua")
  )
)

description := "The Beangle DB Utility"
homepage := Some(uri("https://beangle.github.io/sqlplus/index.html"))

val beangle_commons = "org.beangle.commons" % "beangle-commons" % "6.3.3"
val beangle_template = "org.beangle.template" % "beangle-template" % "0.2.12"
val beangle_jdbc = "org.beangle.jdbc" % "beangle-jdbc" % "1.1.17"

val jline_version = "4.4.3"
val jline = "org.jline" % "jline" % jline_version
val jline_terminal_jni = "org.jline" % "jline-terminal-jni" % jline_version

val commonDeps = Seq(beangle_commons, beangle_jdbc, beangle_template, jline, jline_terminal_jni,
  logback_classic, logback_core,
  scalatest, HikariCP, plantuml, freemarker,
  postgresql, h2, jtds, ojdbc11, orai18n, mysql_connector_java, mssql_jdbc,
  "org.duckdb" % "duckdb_jdbc" % "1.5.5.1")

lazy val root = (project in file("."))
  .settings(
    name := "beangle-sqlplus",
    common,
    Compile / mainClass := Some("org.beangle.sqlplus.shell.Main"),
    libraryDependencies ++= commonDeps
  )
