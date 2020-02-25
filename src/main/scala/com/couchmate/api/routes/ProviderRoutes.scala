package com.couchmate.api.routes

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.Server
import com.couchmate.api.ApiFunctions
import com.couchmate.api.sse.{ProviderHandler, SSEHandler}
import com.couchmate.services.ClusterSingletons
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives

trait ProviderRoutes
  extends ApiFunctions
  with ClusterSingletons
  with HttpMetricsDirectives {
  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
  implicit val ctx: ActorContext[Server.Command]

  private[api] val providerRoutes: Route =
    path("provider") {
      get {
        parameters('zipCode, 'country.?) { (zipCode: String, country: Option[String]) =>
          val handler =
            SSEHandler(
              ProviderHandler.sse(
                zipCode,
                country,
                providerCoordinator,
              )
            )

          complete(handler)
        }
      }
    }
}
