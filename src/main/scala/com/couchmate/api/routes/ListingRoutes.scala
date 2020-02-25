package com.couchmate.api.routes

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.Server
import com.couchmate.api.ApiFunctions
import com.couchmate.api.sse.{ListingHandler, SSEHandler}
import com.couchmate.services.ClusterSingletons
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives

trait ListingRoutes
  extends ApiFunctions
  with ClusterSingletons
  with HttpMetricsDirectives {
  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
  implicit val ctx: ActorContext[Server.Command]

  private[api] val listingRoutes: Route =
    pathLabeled("listing" / Segment, "listing/:listing-id") { id: String =>
      get {
        val handler =
          SSEHandler(
            ListingHandler.sse(
              id,
              listingCoordinator,
            )
          )

        complete(handler)
      }
    }
}
