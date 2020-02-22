package com.couchmate.api.routes

import akka.NotUsed
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.couchmate.Server
import com.couchmate.api.sse.{ListingHandler, SSEHandler}
import com.couchmate.data.models.Lineup
import com.couchmate.services.thirdparty.gracenote.listing.{ListingCoordinator, ListingIngestor, ListingPullType}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext

object ListingRoutes extends PlayJsonSupport {
  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

  def apply(
    listingCoordinator: ActorRef[ListingCoordinator.Command],
  )(
    implicit
    ctx: ActorContext[Server.Command],
    timeout: Timeout,
    ec: ExecutionContext,
  ): Route = {
    path("listing" / Segment) { id: String =>
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
}
