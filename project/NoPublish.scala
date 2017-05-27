import sbt._
import sbt.Keys._

object NoPublish extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  override def projectSettings = Seq(
    publish := (),
    publishLocal := (),
    publishArtifact := false
  )

}


