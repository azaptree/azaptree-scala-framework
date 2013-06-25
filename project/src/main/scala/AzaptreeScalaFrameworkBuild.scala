import sbt._
import Keys._

object AzaptreeScalaFrameworkBuild extends Build {
  
  val projectId = "azaptree-scala-framework"

  println(String.format("*** Building %s",projectId))
  
  val akkaVersion = "2.2.0-RC1"

  lazy val root = Project(id = projectId, base = file("."))
   

}