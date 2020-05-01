import _root_.sbtcrossproject.CrossPlugin.autoImport.crossProject

ThisBuild / scalaVersion := "2.12.8"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "be.dyadics"
ThisBuild / organizationName := "Dyadics"

val http4sVersion = "0.21.2"

val commonSettings = Seq(
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
)

lazy val root = (project in file("."))
  .settings(
    name := "Teeko",
    scalacOptions ++= Seq(
      "-Xfatal-warnings"
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .settings(commonSettings :_*)
  .settings(
    libraryDependencies ++= Seq()
  )

lazy val backend = project
  .dependsOn(shared.jvm)
  .enablePlugins(WebScalaJSBundlerPlugin, BuildEnvPlugin, JavaAppPackaging)
  .settings(Revolver.enableDebugging(port = 5005, suspend = false)) // Activate debugging with the `reStart` command. Because it's handy. :)
  .settings(commonSettings :_*)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "1.0.0-RC18-2",
      "dev.zio" %% "zio-streams" % "1.0.0-RC18-2",
      "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC13",
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "io.circe" %% "circe-generic" % "0.13.0",
      "com.github.pureconfig" %% "pureconfig" % "0.12.1",
      "org.scalameta" %% "munit" % "0.4.3" % Test,
      "org.scalameta" %% "munit-scalacheck" % "0.7.3" % Test,
      "org.scalacheck" %% "scalacheck" % "1.14.1" % Test,
    )
  )
  .settings(
    /*
        #### Frontend related settings ####
        See: https://scalacenter.github.io/scalajs-bundler/getting-started.html#sbt-web
     */
    scalaJSProjects := Seq(frontend),
    Assets / pipelineStages := Seq(scalaJSPipeline),
    // -- Hack n°0 --
    // The two following settings change how and where the `sbt-web-scalajs-bundler` plugin produces the assets sources.
    // The default behavior is to produce a WebJar directory hierarchy.
    // It's problematic because this hierarchy contains the name and version of the project and
    // that's not easy to reference in our Http4s router we'll use to serve the assets.
    // So, we simplify the directory hierachy.
    // Everything will be in a "public" dir, accessible via the project resources.
    Assets / WebKeys.packagePrefix := "public/",
    Assets / WebKeys.exportedMappings := (
      for ((file, path) <- (Assets / mappings).value)
        yield file -> ((Assets / WebKeys.packagePrefix).value + path)
      ),
    // The project compilation will trigger the ScalaJS compilation
    Compile / compile := ((Compile / compile) dependsOn scalaJSPipeline).value,
    // The `scalaJSPipeline` tends to be called too often with the prod mode ("fullOpt"), especially when using the Intellij sbt console.
    scalaJSPipeline / devCommands ++=
      Seq("~reStart", "~compile", "~test:compile", "set", "session", "*/*:dumpStructureTo")
  )


lazy val frontend = project
  .enablePlugins(ScalaJSBundlerPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(shared.js)
  .settings(commonSettings:_*)
  .settings(
    libraryDependencies ++= Seq(
      "io.github.outwatch" %%% "outwatch"  % "1.0.0-RC2",
    ),
//    Compile / npmDependencies += "bulma" -> "0.7.5"
  )
  .settings(
    scalacOptions += "-P:scalajs:sjsDefinedByDefault",
    useYarn := true, // makes scalajs-bundler use yarn instead of npm
//    requireJsDomEnv in Test := true,
    scalaJSUseMainModuleInitializer := true,
    // configure Scala.js to emit a JavaScript module instead of a top-level script
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
    // https://scalacenter.github.io/scalajs-bundler/cookbook.html#performance
    webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly()
  )