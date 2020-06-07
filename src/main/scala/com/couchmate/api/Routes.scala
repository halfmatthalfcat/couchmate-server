package com.couchmate.api

import java.util.UUID

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import com.couchmate.Server
import com.couchmate.api.ws.Commands._
import com.couchmate.api.ws.protocol.Protocol
import com.couchmate.util.akka.WSActor
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives
import fr.davit.akka.http.metrics.prometheus.PrometheusRegistry
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers._

import scala.concurrent.ExecutionContext

trait Routes
  extends CorsDirectives
  with HttpMetricsDirectives {
  import com.couchmate.api.ws._
  implicit val ec: ExecutionContext
  implicit val context: ActorContext[Server.Command]

  def routes(
    registry: PrometheusRegistry,
  ): Route = cors() {
    concat(
      path("healthcheck") {
        complete(StatusCodes.OK)
      },
      path("metrics") {
        get {
          metrics(registry)
        }
      },
      path("ws") {
        handleWebSocketMessages(
          WSActor[Command, Protocol](
            WSClient(),
            SocketConnected,
            Complete,
            ConnFailure,
            { p: Protocol => Incoming(p) },
            { case Outgoing(p) => p }
          )
        )
      }
    )
  }
}
