package com.couchmate.api.routes

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.Server
import com.couchmate.api.ApiFunctions
import com.couchmate.api.ws.{SeatHandler, WSHandler}

import scala.concurrent.ExecutionContext

trait RoomRoutes
  extends ApiFunctions {
  implicit val ctx: ActorContext[Server.Command]

  private[api] val roomRoutes: Route = {
    pathPrefix("room") {
      get {
        val handler =
          WSHandler(
            SeatHandler.ws()
          )

        handleWebSocketMessages(handler)
      }
    }
  }
}
