package com.couchmate.api.routes

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.Server
import com.couchmate.api.ApiFunctions
import com.couchmate.services.ClusterSingletons

trait ListingRoutes
  extends ApiFunctions
  with ClusterSingletons {
  implicit val ctx: ActorContext[Server.Command]

  private[api] val listingRoutes: Route =
    pathPrefix("listing") {
      pathLabeled(LongNumber, ":providerId") { id: Long =>
        get {
//          val handler =
//            WSHandler(
//              ListingHandler.ws(
//                id,
//                listingCoordinator,
//              )
//            )
//
//          handleWebSocketMessages(handler)
          complete(StatusCodes.OK)
        }
      } ~
      pathLabeled("grid" / LongNumber, "grid/:providerId") { providerId: Long =>
        parameter(Symbol("page").?) { (page: Option[String]) =>
          get {
//            val handler =
//              WSHandler(
//                GridHandler.ws(
//                  providerId,
//                  page.map(_.toInt).getOrElse(0),
//                )
//              )
//
//            handleWebSocketMessages(handler)
            complete(StatusCodes.OK)
          }
        }
      }
    }
}
