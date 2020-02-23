package com.couchmate.api

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.Server
import com.couchmate.api.routes._
import com.couchmate.data.db._
import com.couchmate.services.thirdparty.gracenote.listing.ListingCoordinator
import com.couchmate.services.thirdparty.gracenote.provider.ProviderCoordinator

import scala.concurrent.ExecutionContext

object Routes {
  def apply(
    providerCoordinator: ActorRef[ProviderCoordinator.Command],
    listingCoordinator: ActorRef[ListingCoordinator.Command],
    database: CMDatabase,
  )(
    implicit
    ctx: ActorContext[Server.Command],
    system: ActorSystem[Nothing],
    executionContext: ExecutionContext,
    timeout: Timeout,
  ): Route = {
    MainRoutes() ~
    pathPrefix("api") {
      UserRoutes() ~
      RoomRoutes() ~
      ProviderRoutes(
        providerCoordinator,
      ) ~
      ListingRoutes(
        listingCoordinator,
      )
    }
  }
}
