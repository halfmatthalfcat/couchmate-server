/**
 * Main SBT Build Script
 */

import Common._
import sbt.Keys._

lazy val common = project.in(file("common"))
  .settings(
    name := "common",
    version := "0.0.1",
    scalaVersion := "2.13.1",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json"            % "2.8.1",
      "com.beachape"      %% "enumeratum"           % "1.5.15",
      "com.beachape"      %% "enumeratum-play-json" % "1.5.17",
      "com.wix"           %% "accord-core"          % "0.7.4",
    )
  )

lazy val db = project.in(file("db"))
  .settings(
    name := "db",
    version := "0.0.1",
    scalaVersion := "2.13.1",
    resolvers += Resolver.jcenterRepo,
    libraryDependencies ++= Seq(
      slick("slick"),
      slick("slick-hikaricp"),
      slickPg(),
      slickPg("play-json"),
      "io.github.nafg"              %%  "slick-migration-api"        % "0.7.0",
      "ch.qos.logback"              %   "logback-classic"           % "1.2.3",
      "com.typesafe.scala-logging"  %%  "scala-logging"             % "3.9.2",
    ),
    addCommandAlias("db", "runMain com.couchmate.db.Migrations")
  ).dependsOn(common)

lazy val sever = project.in(file("server"))
  .settings(
    name := "server",
    version := "0.0.1",
    scalaVersion := "2.13.1",
    libraryDependencies ++= Seq(
      akka("actor-typed"),
      akka("remote"),
      akka("stream-typed"),
      akka("cluster-typed"),
      akka("cluster-sharding-typed"),
      akka("cluster-metrics"),
      akka("cluster-tools"),
      akka("slf4j"),
      "io.getquill"                 %%  "quill-jdbc"                % "3.5.0",
      "com.typesafe.akka"           %%  "akka-http"                 % "10.1.11",
      "com.typesafe.play"           %%  "play-json"                 % "2.8.1",
      "com.typesafe"                %   "config"                    % "1.4.0",
      "com.amazonaws"               %   "aws-java-sdk"              % "1.11.705",
      "com.nimbusds"                %   "nimbus-jose-jwt"           % "4.27",
      "de.heikoseeberger"           %%  "akka-http-play-json"       % "1.30.0",
      "ch.qos.logback"              %   "logback-classic"           % "1.2.3",
      "com.typesafe.scala-logging"  %%  "scala-logging"             % "3.9.2",
      "org.postgresql"              %   "postgresql"                % "42.2.9",
      "com.github.t3hnar"           %%  "scala-bcrypt"              % "4.1"
    ),
    mainClass in Compile := Some("com.couchmate.Server"),
    mainClass in (Compile, run) := Some("com.couchmate.Server"),
    scalacOptions ++= Seq(
      // "-Xfatal-warnings",
      // "-Xlog-implicits",
      // "-deprecation",
      // "-feature",
      // "-unchecked",
      "-language:postfixOps"
    ),
    // addCommandAlias("mg", "runMain com.couchmate.data.schema.Migrations")
  ).dependsOn(common)
