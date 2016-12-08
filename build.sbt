import com.typesafe.sbt.pgp.PgpKeys.publishSigned
import ReleaseTransformations._

crossScalaVersions := Seq("2.11.8", "2.12.0")

lazy val buildSettings = Seq(
  organization := "com.propensive",
  scalaVersion := "2.11.8",
  name := "contextual",
  version := "0.9",
  scalacOptions ++= Seq("-deprecation", "-feature"),
  scmInfo := Some(ScmInfo(url("https://github.com/propensive/contextual"),
    "scm:git:git@github.com:propensive/contextual.git"))
)

lazy val core = project
  .in(file("core"))
  .settings(buildSettings: _*)
  .settings(publishSettings: _*)
  .settings(libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value)
  .settings(moduleName := "contextual")

lazy val dsls = project
  .in(file("dsls"))
  .settings(buildSettings: _*)
  .settings(publishSettings: _*)
  .settings(moduleName := "contextual-dsls")
  .dependsOn(core)

lazy val examples = project
  .in(file("examples"))
  .settings(buildSettings: _*)
  .settings(moduleName := "contextual-examples")
  .dependsOn(core, dsls)

lazy val publishSettings = Seq(
  homepage := Some(url("http://co.ntextu.al/")),
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  autoAPIMappings := true,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if(isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := (
    <developers>
      <developer>
        <id>propensive</id>
        <name>Jon Pretty</name>
        <url>https://github.com/propensive/contextual/</url>
      </developer>
    </developers>
  ),
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
    pushChanges
  ),
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value
)
lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val noSourceSettings = Seq(
  sources in Compile := Seq(),
  sources in Test := Seq()
)

import java.io.File

def crossVersionSharedSources()  = Seq( 
 (unmanagedSourceDirectories in Compile) ++= { (unmanagedSourceDirectories in Compile ).value.map {
     dir:File => new File(dir.getPath + "_" + scalaBinaryVersion.value)}}
)

lazy val scalaMacroDependencies: Seq[Setting[_]] = Seq(
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      // if scala 2.11+ is used, quasiquotes are merged into scala-reflect
      case Some((2, scalaMajor)) if scalaMajor >= 11 => Seq()
      // in Scala 2.10, quasiquotes are provided by macro paradise
      case Some((2, 10)) =>
        Seq(
          compilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full),
              "org.scalamacros" %% "quasiquotes" % "2.1.0-M5" cross CrossVersion.binary
        )
    }
  }
)

credentials ++= (for {
  username <- Option(System.getenv().get("SONATYPE_USERNAME"))
  password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq

