import sbt._
import sbt.Keys._

import fommil.SensiblePlugin
import scala.scalanative.sbtplugin.ScalaNativePlugin

object NativeWorkarounds extends AutoPlugin {
  override def requires = ScalaNativePlugin && SensiblePlugin
  override def trigger = allRequirements

  override def projectSettings = Seq(
    // Scala Native not yet available for 2.12.x
    crossScalaVersions := Seq("2.11.11")
  )

}


