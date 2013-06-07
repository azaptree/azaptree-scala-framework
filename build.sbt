name := "azaptree-scala-framework"

version := "0.0.1-SNAPSHOT"

scalaVersion in ThisBuild := "2.10.1"

autoCompilerPlugins := true

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test" 

libraryDependencies += "com.typesafe" % "config" % "1.0.1"

libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % akkaVersion,
						    "com.typesafe.akka" %% "akka-remote" % akkaVersion,
                            "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",  
						  	"com.typesafe.akka" %% "akka-transactor" % akkaVersion,
							"com.typesafe.akka" %% "akka-agent" % akkaVersion,
							"com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
							"com.typesafe.akka" %% "akka-dataflow" % akkaVersion)
							
libraryDependencies += compilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.1")

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.12"

scalacOptions += "-P:continuations:enable"

scalariformSettings

net.virtualvoid.sbt.graph.Plugin.graphSettings


