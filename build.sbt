name := "azaptree-scala-framework"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.10.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test" 

libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % "2.1.2",
                            "com.typesafe.akka" %% "akka-testkit" % "2.1.2" % "test",  
						  	"com.typesafe.akka" %% "akka-transactor" % "2.1.2",
							"com.typesafe.akka" %% "akka-agent" % "2.1.2",
							"com.typesafe.akka" %% "akka-slf4j" % "2.1.2")

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.12"

scalariformSettings

net.virtualvoid.sbt.graph.Plugin.graphSettings


