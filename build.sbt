// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.crossProject
import com.softwaremill.PublishTravis.publishTravisSettings

val v2_12 = "2.12.8"
val v2_13 = "2.13.0"

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .settings(buildSettings)
  .settings(publishSettings)
  .settings(scalaMacroDependencies)
  .settings(moduleName := "contextual")

lazy val coreJVM = core.jvm
lazy val coreJS = core.js

lazy val data = crossProject(JVMPlatform, JSPlatform)
  .in(file("data"))
  .settings(buildSettings)
  .settings(noPublishSettings)
  .settings(scalaMacroDependencies)
  .settings(moduleName := "contextual-data")
  .dependsOn(core)

lazy val dataJVM = data.jvm
lazy val dataJS = data.js

lazy val root = (project in file("."))
  .aggregate(coreJVM, coreJS, dataJVM, dataJS)
  .settings(buildSettings)
  .settings(publishSettings)
  .settings(publishTravisSettings)
  .settings(noPublishSettings)

lazy val buildSettings = Seq(
  organization := "com.propensive",
  name := "contextual",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-Ywarn-value-discard",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
  ),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v <= 12 =>
        Seq(
          "-Xexperimental",
          "-Xfuture",
          "-Ywarn-nullary-unit",
          "-Ywarn-inaccessible",
          "-Ywarn-adapted-args"
        )
      case _ =>
        Nil
    }
  },
  scmInfo := Some(
    ScmInfo(url("https://github.com/propensive/contextual"),
      "scm:git:git@github.com:propensive/contextual.git")
  ),
  crossScalaVersions := v2_12 :: v2_13 :: Nil,
  scalaVersion := crossScalaVersions.value.head
)

lazy val publishSettings = ossPublishSettings ++ Seq(
  homepage := Some(url("http://propensive.com/")),
  organizationHomepage := Some(url("http://propensive.com/")),
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  developers := List(
    Developer(
      id = "propensive",
      name = "Jon Pretty",
      email = "",
      url = new URL("https://github.com/propensive/contextual/")
    )
  ),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/propensive/" + name.value),
      "scm:git:git@github.com/propensive/" + name.value + ".git"
    )
  ),
  sonatypeProfileName := "com.propensive",
)

lazy val unmanagedSettings = unmanagedBase :=
  baseDirectory.value / "lib" /
    (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((major, minor)) => s"$major.$minor"
      case _ => ""
    })

lazy val scalaMacroDependencies: Seq[Setting[_]] = Seq(
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
  libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
)
