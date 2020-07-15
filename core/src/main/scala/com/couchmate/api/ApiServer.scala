package com.couchmate.api

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.http.scaladsl.Http
import akka.management.scaladsl.AkkaManagement
import akka.stream.Materializer
import com.couchmate.Server
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsRoute._
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsSettings
import fr.davit.akka.http.metrics.prometheus.PrometheusRegistry

import scala.concurrent.{ExecutionContext, Future}

class ApiServer(
  host: String,
  port: Int,
  registry: PrometheusRegistry,
  settings: HttpMetricsSettings,
  mgmt: AkkaManagement
)(
  implicit
  val ec: ExecutionContext,
  val ctx: ActorContext[Server.Command],
) extends Routes {
  private[this] implicit val actorSystem: ClassicActorSystem =
    ctx.system.toClassic
  private[this] implicit val materializer: Materializer =
    Materializer(ctx.system)

  val httpServer: Future[Http.ServerBinding] = Http().bindAndHandle(
    routes(
      registry,
      mgmt.routes,
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
    mgmt: AkkaManagement
  )(
    implicit
    ec: ExecutionContext,
    ctx: ActorContext[Server.Command],
  ): Future[Http.ServerBinding] = new ApiServer(
    host,
    port,
    registry,
    settings,
    mgmt
  ).httpServer
}
