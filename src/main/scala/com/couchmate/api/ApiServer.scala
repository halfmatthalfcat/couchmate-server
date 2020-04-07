package com.couchmate.api

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.couchmate.Server
import com.typesafe.config.Config
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsRoute._

import scala.concurrent.{ExecutionContext, Future}

class ApiServer(
  val host: String,
  val port: Int,
  val config: Config,
)(
  implicit
  val system: ActorSystem[Nothing],
  val ec: ExecutionContext,
  val ctx: ActorContext[Server.Command],
) extends Routes
  with ApiMetrics {
  private[this] implicit val actorSystem: ClassicActorSystem =
    system.toClassic
  private[this] implicit val materializer: ActorMaterializer =
    ActorMaterializer()(actorSystem)

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
    config: Config,
  )(
    implicit
    system: ActorSystem[Nothing],
    ec: ExecutionContext,
    ctx: ActorContext[Server.Command],
  ): Future[Http.ServerBinding] = new ApiServer(
    host,
    port,
    config,
  ).httpServer
}
