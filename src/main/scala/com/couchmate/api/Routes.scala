package com.couchmate.api

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.api.routes.{MainRoutes, RoomRoutes, SourceRoutes, UserRoutes}
import com.couchmate.services.data.source.SourceService

import scala.concurrent.ExecutionContext

object Routes {
  def apply(
    sourceServiceRouter: ActorRef[SourceService.Command],
  )(
    implicit
    actorSystem: ActorSystem[Nothing],
    executionContext: ExecutionContext,
    timeout: Timeout,
  ): Route = {
    MainRoutes() ~
    pathPrefix("api") {
      UserRoutes() ~
      RoomRoutes() ~
      SourceRoutes(sourceServiceRouter)
    }
  }
}
