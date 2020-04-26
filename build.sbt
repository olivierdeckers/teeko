ThisBuild / scalaVersion := "2.13.1"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "be.dyadics"
ThisBuild / organizationName := "Dyadics"

val http4sVersion = "0.21.2"

lazy val root = (project in file("."))
  .settings(
    name := "Teeko",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "1.0.0-RC18-2",
      "dev.zio" %% "zio-streams" % "1.0.0-RC18-2",
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "io.circe" %% "circe-generic" % "0.13.0",
      "org.scalameta" %% "munit" % "0.4.3" % Test,
      "org.scalameta" %% "munit-scalacheck" % "0.7.3" % Test,
      "org.scalacheck" %% "scalacheck" % "1.14.1" % Test,
    ),
    scalacOptions ++= Seq(
      "-Xfatal-warnings"
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
