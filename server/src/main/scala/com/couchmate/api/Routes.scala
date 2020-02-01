package com.couchmate.api

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.api.routes._
import com.couchmate.data.db.{ProviderDAO, ProviderOwnerDAO}

import scala.concurrent.ExecutionContext

object Routes {
  def apply(
    providerDAO: ProviderDAO,
    providerOwnerDAO: ProviderOwnerDAO,
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
      ProviderRoutes(
        providerDAO,
        providerOwnerDAO,
      ) ~
      ListingRoutes()
    }
  }
}
