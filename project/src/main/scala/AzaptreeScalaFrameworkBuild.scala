import sbt._
import Keys._

object AzaptreeScalaFrameworkBuild extends Build {
  
  val projectId = "azaptree-scala-framework"

  println(String.format("*** Building %s",projectId))

  lazy val root = Project(id = projectId, base = file("."))

}