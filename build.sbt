/**
 * Main SBT Build Script
 */

import Common._
import Release._
import com.github.scala2ts.configuration.{RenderAs, SealedTypesMapping}
import sbt.Keys._
import sbt.Resolver
import ReleaseTransformations._

lazy val tsSettings = Seq(
  tsEnable := true,
  tsRenderAs := RenderAs.Class,
  tsIncludeDiscriminator := true,
  tsDiscriminatorName := "ttype",
  tsIncludeTypes := Seq(
    "com\\.couchmate\\.api\\.ws\\.protocol".r
  ),
  tsSealedTypesMapping := SealedTypesMapping.AsUnionString,
  tsOutDir := s"${(target in Compile).value}/typescript",
  tsPackageJsonName := "@couchmate/server",
  tsPackageJsonVersion := version.value,
  tsPackageJsonRegistry := "https://npm.pkg.github.com"
)

lazy val server = (project in file("."))
  .enablePlugins(
    Scala2TSPlugin,
    JavaAppPackaging,
    DockerPlugin,
  )
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
      akka("persistence-typed"),
      akka("cluster-metrics"),
      akka("cluster-tools"),
      akka("slf4j"),
      alpakka("amqp"),
      alpakka("slick"),
      slick("slick"),
      slick("slick-hikaricp"),
      slickPg(),
      slickPg("play-json"),
      // Akka HTTP Stuff
      "com.typesafe.akka"           %%  "akka-http"                     % "10.1.11",
      "ch.megard"                   %%  "akka-http-cors"                % "0.4.3",
      "de.heikoseeberger"           %%  "akka-http-play-json"           % "1.30.0",
      "fr.davit"                    %%  "akka-http-metrics-prometheus"  % "0.6.0",
      // Misc
      "io.underscore"               %%  "slickless"                     % "0.3.6",
      "io.github.nafg"              %%  "slick-migration-api"           % "0.7.0",
      "com.liyaos"                  %%  "scala-forklift-slick"          % "0.3.2",
      "com.typesafe.play"           %%  "play-json"                     % "2.8.1",
      "org.julienrf"                %%  "play-json-derived-codecs"      % "7.0.0",
      "com.typesafe"                %   "config"                        % "1.4.0",
      "com.nimbusds"                %   "nimbus-jose-jwt"               % "4.27",
      "ch.qos.logback"              %   "logback-classic"               % "1.2.3",
      "com.typesafe.scala-logging"  %%  "scala-logging"                 % "3.9.2",
      "org.postgresql"              %   "postgresql"                    % "42.2.9",
      "com.github.t3hnar"           %%  "scala-bcrypt"                  % "4.1",
      "com.typesafe.play"           %%  "play-json"                     % "2.8.1",
      "com.beachape"                %%  "enumeratum"                    % "1.5.15",
      "com.beachape"                %%  "enumeratum-play-json"          % "1.5.17",
      "com.beachape"                %%  "enumeratum-slick"              % "1.5.16",
      "com.github.halfmatthalfcat"  %%  "scala-moniker"                 % "0.0.1",
      "com.chuusai"                 %%  "shapeless"                     % "2.3.3",
      "com.neovisionaries"          %   "nv-i18n"                       % "1.27",
      "org.fusesource.leveldbjni"   %  "leveldbjni-all"                 % "1.8"
    ),
    mainClass in Compile := Some("com.couchmate.Server"),
    discoveredMainClasses in Compile := Seq(),
    mainClass in (Compile, run) := Some("com.couchmate.Server"),
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-language:postfixOps",
    ),
    releaseVersionBump := sbtrelease.Version.Bump.Bugfix,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      runCompile,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommand("sonatypeRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    ),
    pomExtra :=
      <url>https://www.github.com/couchmate/server</url>
        <scm>
          <url>git@github.com:couchmate/server.git</url>
          <connection>scm:git:git@github.com:couchmate/couchmate.git</connection>
        </scm>
        <developers>
          <developer>
            <id>halfmatthalfcat</id>
            <name>Matt Oliver</name>
            <url>https://www.github.com/halfmatthalfcat</url>
          </developer>
        </developers>,
    publishMavenStyle := true,
    githubOwner := "couchmate",
    githubRepository := "server",
    githubTokenSource := TokenSource.Environment("GITHUB_TOKEN"),
    publishTo := githubPublishTo.value,
    addCommandAlias("db", "runMain com.couchmate.data.db.Migrations")
  )
