package com.couchmate.api.routes

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.couchmate.data.thirdparty.gracenote.GracenoteChannelAiring
import com.couchmate.services.thirdparty.gracenote.ListingIngestor
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext

object ListingRoutes extends PlayJsonSupport {
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  def apply(
    listingIngestor: ListingIngestor,
  )(
    implicit
    actorSystem: ActorSystem[Nothing],
    timeout: Timeout,
    ec: ExecutionContext,
  ): Route = {
    path("listing" / Segment) { id: String =>
      get {
        val listings = listingIngestor.ingestListings(
          id,
        )

        complete(listings)
      }
    }
  }
}
