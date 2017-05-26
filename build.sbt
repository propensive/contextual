
val core = crossProj("core")
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.typelevel" %% "macro-compat" % "1.1.1",
      compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch)
    ),
    moduleName := "contextual" // intentional override
  )

val coreJVM = core.jvm
val coreJS = core.js
val coreNative = core.native

val examples = crossProj("examples")
  .settings(quasiQuotes)
  .dependsOn(core)

val examplesJVM = examples.jvm
val examplesJS = examples.js
val examplesNative = examples.native

val tests = crossProj("tests", sbtcrossproject.CrossType.Full)
  .settings(
    publish := (),
    publishLocal := (),
    publishArtifact := false
  )
  .settings(quasiQuotes)
  .dependsOn(examples)

val testsJVM = tests.jvm
val testsJS = tests.js
val testsNative = tests.native
