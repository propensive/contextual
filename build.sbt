import sbtcrossproject.{CrossProject, CrossType}

inThisBuild(Seq(
  organization := "com.propensive",
  sonatypeGithub := ("propensive", "contextual"),
  licenses := Seq(Apache2),
  scalaVersion := "2.12.2",
  name := "contextual",
  scalacOptions ++= Seq("-Ywarn-value-discard", "-Ywarn-nullary-unit", "-Ywarn-numeric-widen", "-Ywarn-inaccessible"),
  crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.2")
))

// WORKAROUND: https://github.com/scala-native/scala-native/issues/742
def cross(dir: String, t: CrossType) =
  CrossProject(dir, file(dir), t, JSPlatform, JVMPlatform, NativePlatform)
    .nativeSettings(
      // Scala Native not yet available for 2.12.x
      scalaVersion := "2.11.11",
      crossScalaVersions := Seq("2.11.11")
    )
    .settings(moduleName := s"contextual-${dir}")

lazy val core = cross("core", CrossType.Pure)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.typelevel" %% "macro-compat" % "1.1.1",
      compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch)
    ),
    moduleName := "contextual" // intentional override
  )

lazy val coreJVM = core.jvm
lazy val coreJS = core.js
lazy val coreNative = core.native

lazy val examples = cross("examples", CrossType.Pure)
  .settings(quasiQuotesDependencies)
  .dependsOn(core)

lazy val examplesJVM = examples.jvm
lazy val examplesJS = examples.js
lazy val examplesNative = examples.native

lazy val tests = cross("tests", CrossType.Full)
  .settings(
    publish := (),
    publishLocal := (),
    publishArtifact := false
  )
  .settings(quasiQuotesDependencies)
  .dependsOn(examples)

lazy val testsJVM = tests.jvm
lazy val testsJS = tests.js
lazy val testsNative = tests.native

lazy val quasiQuotesDependencies: Seq[Setting[_]] =
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 10)) => Seq(
        compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch),
        "org.scalamacros" %% "quasiquotes" % "2.1.0"
      )
      case _ => Nil
    }
  }
