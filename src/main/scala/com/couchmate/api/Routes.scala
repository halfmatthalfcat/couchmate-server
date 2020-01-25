package com.couchmate.api

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.alpakka.slick.scaladsl.SlickSession
import akka.util.Timeout
import com.couchmate.api.routes.{MainRoutes, RoomRoutes, SourceRoutes, UserRoutes}

import scala.concurrent.ExecutionContext

object Routes {
  def apply()(
    implicit
    actorSystem: ActorSystem[Nothing],
    executionContext: ExecutionContext,
    timeout: Timeout,
    session: SlickSession,
  ): Route = {
    MainRoutes() ~
    pathPrefix("api") {
      UserRoutes() ~
      RoomRoutes() ~
      SourceRoutes()
    }
  }
}
