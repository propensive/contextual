import com.typesafe.sbt.pgp.PgpKeys.publishSigned
import ReleaseTransformations._

lazy val buildSettings = Seq(
  organization := "com.propensive",
  scalaVersion := "2.11.8",
  name := "contextual",
  version := "1.0",
  scalacOptions ++= Seq("-deprecation", "-feature")
)

lazy val contextual = project
  .in(file("."))
  .settings(buildSettings: _*)
  .settings(name := "contextual")
  .dependsOn(core, dsls, examples)

lazy val core = project
  .in(file("core"))
  .settings(buildSettings: _*)
  .settings(version := "1.0")
  .settings(libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value)
  .settings(moduleName := "contextual-core")

lazy val dsls = project
  .in(file("dsls"))
  .settings(buildSettings: _*)
  .settings(moduleName := "contextual-dsls")
  .dependsOn(core)

lazy val examples = project
  .in(file("examples"))
  .settings(buildSettings: _*)
  .settings(moduleName := "contextual-examples")
  .dependsOn(core, dsls)
