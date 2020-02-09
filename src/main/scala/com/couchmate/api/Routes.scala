package com.couchmate.api

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.api.routes._
import com.couchmate.data.db._
import com.couchmate.services.thirdparty.gracenote.{ListingIngestor, ProviderIngestor}

import scala.concurrent.ExecutionContext

object Routes {
  def apply(
    providerIngestor: ProviderIngestor,
    listingIngestor: ListingIngestor,
    database: CMDatabase,
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
        database,
        providerIngestor,
      ) ~
      ListingRoutes(
        listingIngestor,
      )
    }
  }
}
