import org.beangle.parent.Dependencies.*
import org.beangle.parent.Settings.*

ThisBuild / organization := "org.beangle.db"
ThisBuild / version := "0.0.37-SNAPSHOT"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/beangle/db"),
    "scm:git@github.com:beangle/db.git"
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

ThisBuild / description := "The Beangle DB Plus"
ThisBuild / homepage := Some(url("https://beangle.github.io/db/index.html"))

val beangle_commons_core = "org.beangle.commons" %% "beangle-commons-core" % "5.6.14"
val beangle_data_jdbc = "org.beangle.data" %% "beangle-data-jdbc" % "5.8.8"
val beangle_template_freemarker = "org.beangle.template" %% "beangle-template-freemarker" % "0.1.11"
val commonDeps = Seq(beangle_commons_core, logback_classic, logback_core, beangle_data_jdbc, scalatest, HikariCP, postgresql, h2, jtds, ojdbc11, orai18n, mysql_connector_java, mssql_jdbc)

lazy val root = (project in file("."))
  .settings(
    name := "beangle-db-plus",
    common,
    Compile / mainClass := Some("org.beangle.db.shell.Main"),
    libraryDependencies ++= commonDeps,
    libraryDependencies ++= Seq(beangle_commons_core, beangle_template_freemarker, plantuml)
  )

