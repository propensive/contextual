import sbt._
import sbt.Keys._

import fommil.SensiblePlugin
import fommil.SonatypeKeys._
import sbtcrossproject._
import scala.scalanative.sbtplugin.NativePlatform
import scalajscrossproject.JSPlatform

object ProjectPlugin extends AutoPlugin {
  override def requires = SensiblePlugin
  override def trigger  = allRequirements

  val autoImport = ProjectPluginKeys
  import autoImport._

  // NOTE: everything in here is applied once, to the entire build
  override val buildSettings = Seq(
    organization := "com.propensive",
    sonatypeGithub := "propensive" -> "contextual",
    licenses := Seq(Apache2),
    scalaVersion := "2.12.2",
    name := "contextual",
    scalacOptions ++= Seq("-Ywarn-value-discard", "-Ywarn-nullary-unit", "-Ywarn-numeric-widen", "-Ywarn-inaccessible"),
    crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.2")
  )

  // NOTE: everything in here is applied to every project (a better `commonSettings`)
  override val projectSettings = Seq(
    moduleName := (name in ThisBuild).value + "-" + name.value
  )

}

object ProjectPluginKeys {
  // NOTE: anything in here is automatically visible in build.sbt

  val quasiQuotes: Seq[Setting[_]] = Seq(
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 10)) => Seq(
          compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch),
          "org.scalamacros" %% "quasiquotes" % "2.1.0"
        )
        case _ => Nil
      }
    }
  )

  // it's less code to duplicate the project name than to use the macro
  def crossProj(dir: String, t: CrossType = CrossType.Pure) =
    CrossProject(dir, file(dir), t, JSPlatform, JVMPlatform, NativePlatform)

}
