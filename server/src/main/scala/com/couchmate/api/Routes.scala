package com.couchmate.api

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.api.routes._
import com.couchmate.data.schema.PgProfile.api._
import com.couchmate.services.thirdparty.gracenote.GracenoteService

import scala.concurrent.ExecutionContext

object Routes {
  def apply()(
    implicit
    actorSystem: ActorSystem[Nothing],
    executionContext: ExecutionContext,
    timeout: Timeout,
    db: Database,
    gracenoteService: GracenoteService,
  ): Route = {
    MainRoutes() ~
    pathPrefix("api") {
      UserRoutes() ~
      RoomRoutes() ~
      ProviderRoutes() ~
      ListingRoutes()
    }
  }
}
