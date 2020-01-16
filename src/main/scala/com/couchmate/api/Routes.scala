package com.couchmate.api

import akka.actor.ActorSystem
import akka.util.Timeout
import com.couchmate.api.routes.{MainRoutes, RoomRoutes, UserRoutes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.ExecutionContext

object Routes {
  def apply()(
    implicit
    actorSystem: ActorSystem,
    executionContext: ExecutionContext,
    timeout: Timeout,
  ): Route = {
    MainRoutes() ~
    pathPrefix("api") {
      UserRoutes() ~
      RoomRoutes()
    }
  }
}
