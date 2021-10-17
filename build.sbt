import org.beangle.parent.Dependencies._
import org.beangle.parent.Settings._

ThisBuild / organization := "org.beangle.db"
ThisBuild / version := "0.0.13"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/beangle/db"),
    "scm:git@github.com:beangle/db.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id    = "chaostone",
    name  = "Tihua Duan",
    email = "duantihua@gmail.com",
    url   = url("http://github.com/duantihua")
  )
)

ThisBuild / description := "The Beangle DB Library"
ThisBuild / homepage := Some(url("https://beangle.github.io/db/index.html"))

val beangle_data_jdbc = "org.beangle.data" %% "beangle-data-jdbc" % "5.3.25"
val beangle_template_freemarker = "org.beangle.template" %% "beangle-template-freemarker" % "0.0.34"
val commonDeps = Seq(logback_classic, logback_core, beangle_data_jdbc, scalatest)

lazy val root = (project in file("."))
  .settings()
  .aggregate(lint,report,transport)

lazy val lint = (project in file("lint"))
  .settings(
    name := "beangle-db-lint",
    common,
    libraryDependencies ++= commonDeps
  )

lazy val report = (project in file("report"))
  .settings(
    name := "beangle-db-report",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(HikariCP,beangle_template_freemarker,postgresql,plantuml))
  )

lazy val transport = (project in file("transport"))
  .settings(
    name := "beangle-db-transport",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(postgresql,h2,jtds,ojdbc11,orai18n,mysql_connector_java,mssql_jdbc,HikariCP))
  )

publish / skip := true
