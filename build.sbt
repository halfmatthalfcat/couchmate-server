/**
 * Main SBT Build Script
 */

import Common._
import Release._
import com.github.scala2ts.configuration.{RenderAs, SealedTypesMapping}
import sbt.Keys._
import sbt.Resolver
import ReleaseTransformations._

val commonSettings = Seq(
  organization := "com.couchmate",
  scalaVersion := "2.13.1",
  resolvers ++= Seq(
    Resolver.jcenterRepo,
    Resolver.sonatypeRepo("snapshot")
  ),
  githubOwner := "couchmate",
  githubRepository := "server",
  githubTokenSource := TokenSource.GitConfig("github.token") || TokenSource.Environment("GITHUB_TOKEN"),
  publishTo := githubPublishTo.value,
)

lazy val server = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(common, migration, core)

lazy val common = (project in file("common"))
  .settings(commonSettings: _*)
  .settings(
    name := "common",
    libraryDependencies ++= Seq(
      akka("stream-typed"),
      alpakka("slick"),
      slick("slick"),
      slick("slick-hikaricp"),
      slickPg(),
      slickPg("play-json"),
      "org.julienrf"                %%  "play-json-derived-codecs"  % "7.0.0",
      "io.underscore"               %%  "slickless"                 % "0.3.6",
      "com.typesafe.play"           %%  "play-json"                 % "2.8.1",
      "com.beachape"                %%  "enumeratum"                % "1.5.15",
      "com.beachape"                %%  "enumeratum-play-json"      % "1.5.17",
      "com.beachape"                %%  "enumeratum-slick"          % "1.5.16",
      "com.chuusai"                 %%  "shapeless"                 % "2.3.3",
      "org.postgresql"              %   "postgresql"                % "42.2.9",
      "com.neovisionaries"          %   "nv-i18n"                   % "1.27",
      "com.github.t3hnar"           %%  "scala-bcrypt"              % "4.1",
      "com.typesafe.scala-logging"  %%  "scala-logging"             % "3.9.2"
    )
  )

lazy val migration = (project in file("migration"))
  .dependsOn(common)
  .settings(commonSettings: _*)
  .settings(
    name := "migration",
    libraryDependencies ++= Seq(
      "io.github.nafg" %%  "slick-migration-api" % "0.8.0",
    ),
    addCommandAlias("mg", "runMain com.couchmate.migration.Migrations")
  )

lazy val core = (project in file("core"))
  .enablePlugins(
    Scala2TSPlugin,
    JavaAppPackaging,
    DockerPlugin,
  )
  .dependsOn(common)
  .settings(commonSettings: _*)
  .settings(tsSettings: _*)
  .settings(dockerSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      akka("actor-typed"),
      akka("remote"),
      akka("stream-typed"),
      akka("cluster-typed"),
      akka("cluster-sharding-typed"),
      akka("persistence-typed"),
      akka("persistence-query"),
      akka("cluster-metrics"),
      akka("cluster-tools"),
      akka("slf4j"),
      akka("discovery"),
      akkaManagement(),
      akkaManagement("cluster-http"),
      akkaManagement("cluster-bootstrap"),
      // Bootstrapping
      "com.lightbend.akka.discovery"  %%  "akka-discovery-kubernetes-api" % Versions.akkaManagement,
      "com.lightbend.akka"            %%  "akka-persistence-jdbc"         % "4.0.0",
      // Akka HTTP Stuff
      "com.typesafe.akka"             %%  "akka-http"                     % "10.1.11",
      "ch.megard"                     %%  "akka-http-cors"                % "0.4.3",
      "de.heikoseeberger"             %%  "akka-http-play-json"           % "1.30.0",
      "fr.davit"                      %%  "akka-http-metrics-prometheus"  % "0.6.0",
      // Misc
      "com.typesafe"                %   "config"                        % "1.4.0",
      "com.nimbusds"                %   "nimbus-jose-jwt"               % "8.3",
      "ch.qos.logback"              %   "logback-classic"               % "1.2.3",
      "com.github.halfmatthalfcat"  %%  "scala-moniker"                 % "0.0.1",
      "com.enragedginger"           %%  "akka-quartz-scheduler"         % "1.8.4-akka-2.6.x",
      "net.sargue"                  %   "mailgun"                       % "1.9.2",
      "com.lihaoyi"                 %%  "scalatags"                     % "0.8.2",
      "io.lemonlabs"                %%  "scala-uri"                     % "3.0.0",
      "net.ruippeixotog"            %%  "scala-scraper"                 % "2.2.0"
    ),
    mainClass in Compile := Some("com.couchmate.Server"),
    discoveredMainClasses in Compile := Seq(),
    mainClass in (Compile, run) := Some("com.couchmate.Server"),
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-language:postfixOps",
      "-language:implicitConversions"
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
      publishArtifacts,
      stageDockerImage,
      publishDockerImage,
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
    addCommandAlias("jwt", "runMain com.couchmate.util.jwt.Jwt")
  )

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

lazy val dockerSettings = Seq(
  dockerRepository := Some("https://docker.pkg.github.com"),
  dockerUsername := Some("halfmatthalfcat"),
  dockerAlias := DockerAlias(
    Some("docker.pkg.github.com"),
    Some("couchmate"),
    "server/server",
    Some(version.value)
  )
)