package com.couchmate.api

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.http.scaladsl.Http
import akka.stream.Materializer
import akka.util.Timeout
import com.couchmate.Server
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.util.akka.extensions.{JwtExtension, SingletonExtension, UserExtension}
import com.typesafe.config.Config
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsSettings
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsRoute._
import fr.davit.akka.http.metrics.prometheus.PrometheusRegistry

import scala.concurrent.{ExecutionContext, Future}

class ApiServer(
  host: String,
  port: Int,
  registry: PrometheusRegistry,
  settings: HttpMetricsSettings,
)(
  implicit
  val ec: ExecutionContext,
  val ctx: ActorContext[Server.Command],
  system: ActorSystem[Nothing],
  db: Database,
  jwt: JwtExtension,
  user: UserExtension,
  singleton: SingletonExtension,
  config: Config,
  timeout: Timeout
) extends Routes {
  private[this] implicit val actorSystem: ClassicActorSystem =
    ctx.system.toClassic
  private[this] implicit val materializer: Materializer =
    Materializer(ctx.system)

  val httpServer: Future[Http.ServerBinding] = Http().bindAndHandle(
    routes(
      registry,
    ).recordMetrics(registry, settings),
    interface = host,
    port = port,
  )
}

object ApiServer {
  def apply(
    host: String,
    port: Int,
    registry: PrometheusRegistry,
    settings: HttpMetricsSettings,
  )(
    implicit
    ec: ExecutionContext,
    ctx: ActorContext[Server.Command],
    system: ActorSystem[Nothing],
    db: Database,
    jwt: JwtExtension,
    user: UserExtension,
    singletons: SingletonExtension,
    config: Config,
    timeout: Timeout
  ): Future[Http.ServerBinding] = new ApiServer(
    host,
    port,
    registry,
    settings,
  ).httpServer
}
