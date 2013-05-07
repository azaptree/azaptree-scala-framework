================================================================================================================================================
- this will add the typesafe sbteclipse plugin globally to all sbt builds, which can be used to generate Eclipse projects from the sbt build definition.
  - see https://github.com/typesafehub/sbteclipse/wiki/Using-sbteclipse
  - create the following file: ~/.sbt/plugins/build.sbt with the following:
------------------------------------------------------------------------------------------------------------------------------------------------
resolvers += Classpaths.typesafeResolver

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.1.2")
================================================================================================================================================

================================================================================================================================================
- run the following commands from the sbt console to create an Eclipse project for the sbt build project:
------------------------------------------------------------------------------------------------------------------------------------------------
reload plugins
set name := "{project-name}-sbt-build"
set scalaVersion := "{scala-version}"
eclipse execution-environment=JavaSE-1.7 with-source=true
reload return

e.g. 

reload plugins
set name := "azaptree-scala-framework-sbt-build"
set scalaVersion := "2.10.1"
eclipse execution-environment=JavaSE-1.7 with-source=true
reload return
================================================================================================================================================
