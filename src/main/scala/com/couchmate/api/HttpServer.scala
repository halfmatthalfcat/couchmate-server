package com.couchmate.api

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

case class HttpServer()(
  implicit
  val actorSystem: ActorSystem,
  val executionContext: ExecutionContext,
  val timeout: Timeout,
) {
  val httpServer: Future[Http.ServerBinding] = Http().bindAndHandle(
    Routes(),
    interface = "0.0.0.0",
    port = 8080,
  )
}
