package com.couchmate.api.routes

import java.util.UUID

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.Server
import com.couchmate.api.ApiFunctions
import com.couchmate.util.stream.WSActor

trait RoomRoutes
  extends ApiFunctions {
  implicit val ctx: ActorContext[Server.Command]

  private[api] val roomRoutes: Route = {
    pathPrefix("room") {
      get {
        handleWebSocketMessages(
          WSActor.ws(
            UUID.randomUUID()
          )
        )
      }
    }
  }
}
