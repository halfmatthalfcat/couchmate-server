package com.couchmate.api.routes

import com.couchmate.api._

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.ExecutionContext

object RoomRoutes {
  def apply()(
    implicit
    actorSystem: ActorSystem[Nothing],
    executionContext: ExecutionContext,
  ): Route = {
    import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

//    path("room" / LongNumber / "stream") { roomId =>
//      complete(handleSseConnection(roomId))
//    }
    pathPrefix("room") {
      pathEndOrSingleSlash{
        complete("ok")
      }
    }
  }
}
