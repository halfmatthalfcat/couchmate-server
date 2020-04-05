package com.couchmate.api.routes

import java.time.{LocalDateTime, ZoneId, ZoneOffset}

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.Server
import com.couchmate.api.ApiFunctions
import com.couchmate.api.sse.{ListingHandler, SSEHandler}
import com.couchmate.services.ClusterSingletons
import com.couchmate.util.DateUtils

trait ListingRoutes
  extends ApiFunctions
  with ClusterSingletons {
  implicit val ctx: ActorContext[Server.Command]

  private[api] val listingRoutes: Route =
    pathPrefix("listing") {
      pathLabeled(LongNumber, ":providerId") { id: Long =>
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
      } ~
      pathLabeled("grid" / LongNumber, "grid/:providerId") { providerId: Long =>
        async {
          db.grid.getGrid(
            providerId,
            DateUtils.roundNearestHour(LocalDateTime.now(ZoneOffset.UTC)),
            3,
          ).map(StatusCodes.OK -> Some(_))
        }
      }
    }
}
