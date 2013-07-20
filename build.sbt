organization := "com.azaptree"

name := "azaptree-scala-framework"

version := "0.0.1-SNAPSHOT"

scalaVersion in ThisBuild := "2.10.2"

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
							
libraryDependencies += "com.typesafe.akka" %% "akka-cluster-experimental" % "2.2-M3"
							
libraryDependencies += compilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.2-RC1")

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.12"

libraryDependencies += "commons-io" % "commons-io" % "2.4"

libraryDependencies += "commons-codec" % "commons-codec" % "1.8"

libraryDependencies += "org.mongodb" %% "casbah" % "2.6.2"

scalacOptions ++= Seq("-P:continuations:enable",
					  "-optimise",
					  "-feature",
					  "-language:postfixOps",
					  "-language:higherKinds",
					  "-deprecation")

scalariformSettings

net.virtualvoid.sbt.graph.Plugin.graphSettings


