/**
 * Main SBT Build Script
 */

import Common._
import sbt.Keys._

lazy val sever = project.in(file("."))
  .settings(
    name := "server",
    version := "0.0.1",
    scalaVersion := "2.13.1",
    resolvers += Resolver.jcenterRepo,
    libraryDependencies ++= Seq(
      akka("actor-typed"),
      akka("remote"),
      akka("stream-typed"),
      akka("cluster-typed"),
      akka("cluster-sharding-typed"),
      akka("cluster-metrics"),
      akka("cluster-tools"),
      akka("slf4j"),
      slick("slick"),
      slick("slick-hikaricp"),
      slickPg(),
      slickPg("play-json"),
      "com.beachape"                %%  "enumeratum"                % "1.5.15",
      "com.beachape"                %%  "enumeratum-play-json"      % "1.5.17",
      "com.typesafe.akka"           %%  "akka-http"                 % "10.1.11",
      "com.typesafe.play"           %%  "play-json"                 % "2.8.1",
      "com.typesafe"                %   "config"                    % "1.4.0",
      "com.amazonaws"               %   "aws-java-sdk"              % "1.11.705",
      "com.nimbusds"                %   "nimbus-jose-jwt"           % "4.27",
      "de.heikoseeberger"           %%  "akka-http-play-json"       % "1.30.0",
      "com.wix"                     %%  "accord-core"               % "0.7.4",
      "ch.qos.logback"              %   "logback-classic"           % "1.2.3",
      "com.typesafe.scala-logging"  %%  "scala-logging"             % "3.9.2",
      "org.postgresql"              %   "postgresql"                % "42.2.9",
      // "com.github.halfmatthalfcat"  %%  "scala-forklift-slick"      % "0.3.3",
      "com.liyaos"                  %%  "scala-forklift-slick"      % "0.3.2-SNAPSHOT",
      "io.github.nafg"              %%  "slick-migration-api"       % "0.7.0",
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
    addCommandAlias("mg", "runMain com.couchmate.data.schema.Migrations")
  )
