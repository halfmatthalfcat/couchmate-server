/**
 * Main SBT Build Script
 */

import Common._
import sbt.Keys._
import sbt.Resolver

lazy val tsSettings = Seq(
  tsEnable := true,
  tsIncludeTypes := Seq(
    "com\\.couchmate\\.api\\.models".r
  ),
  tsOutDir := s"${(target in Compile).value}/typescript",
  tsPackageJsonName := "@couchmate/server",
  tsPackageJsonVersion := version.value,
  tsPackageJsonRegistry := "https://gitlab.com/api/v4/projects/1/packages/npm/"
)

lazy val server = (project in file("."))
  .enablePlugins(Scala2TSPlugin)
  .settings(tsSettings: _*)
  .settings(
    name := "server",
    version := "0.0.1",
    scalaVersion := "2.13.1",
    resolvers ++= Seq(
      Resolver.jcenterRepo,
      Resolver.sonatypeRepo("snapshot")
    ),
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
      "io.underscore"               %%  "slickless"                     % "0.3.6",
      "io.github.nafg"              %%  "slick-migration-api"           % "0.7.0",
      "com.typesafe.akka"           %%  "akka-http"                     % "10.1.11",
      "ch.megard"                   %%  "akka-http-cors"                % "0.4.2",
      "com.typesafe.play"           %%  "play-json"                     % "2.8.1",
      "com.typesafe"                %   "config"                        % "1.4.0",
      "com.amazonaws"               %   "aws-java-sdk"                  % "1.11.705",
      "com.nimbusds"                %   "nimbus-jose-jwt"               % "4.27",
      "de.heikoseeberger"           %%  "akka-http-play-json"           % "1.30.0",
      "ch.qos.logback"              %   "logback-classic"               % "1.2.3",
      "com.typesafe.scala-logging"  %%  "scala-logging"                 % "3.9.2",
      "org.postgresql"              %   "postgresql"                    % "42.2.9",
      "com.github.t3hnar"           %%  "scala-bcrypt"                  % "4.1",
      "com.typesafe.play"           %%  "play-json"                     % "2.8.1",
      "com.beachape"                %%  "enumeratum"                    % "1.5.15",
      "com.beachape"                %%  "enumeratum-play-json"          % "1.5.17",
      "com.beachape"                %%  "enumeratum-slick"              % "1.5.16",
      "com.wix"                     %%  "accord-core"                   % "0.7.4",
      "fr.davit"                    %%  "akka-http-metrics-prometheus"  % "0.6.0",
      "com.github.halfmatthalfcat"  %%  "scala-moniker"                 % "0.0.1",
      "com.chuusai"                 %%  "shapeless"                     % "2.3.3",
    ),
    mainClass in Compile := Some("com.couchmate.Server"),
    mainClass in (Compile, run) := Some("com.couchmate.Server"),
    scalacOptions ++= Seq(
//      "-deprecation",
      "-encoding", "UTF-8",
//      "-unchecked",
//      "-feature",
      // "-language:implicitConversions",
      "-language:postfixOps",
//      "-Ywarn-dead-code",
//      "-Xlint",
//      "-Xfatal-warnings",
    ),
    addCommandAlias("db", "runMain com.couchmate.data.db.Migrations")
  )
