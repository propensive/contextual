import sbt._
import sbt.Keys._

import org.scalajs.sbtplugin.ScalaJSPlugin
import fommil.SensiblePlugin

object JSWorkarounds extends AutoPlugin {
  override def requires = ScalaJSPlugin && SensiblePlugin
  override def trigger = allRequirements

  override def projectSettings = Seq(
    // forking breaks scalajs
    fork := false,
    // and so do any non-property flags
    javaOptions := javaOptions.value.filter(_.startsWith("-D"))
  )

}


