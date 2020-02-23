package com.couchmate.api.routes

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.Server
import com.couchmate.api.sse.{ProviderHandler, SSEHandler}
import com.couchmate.data.db._
import com.couchmate.data.models.{Provider, ProviderOwner}
import com.couchmate.services.thirdparty.gracenote.provider.{ProviderCoordinator, ProviderIngestor}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext

object ProviderRoutes
  extends PlayJsonSupport {
  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

  def apply(
    providerCoordinator: ActorRef[ProviderCoordinator.Command],
  )(
    implicit
    ctx: ActorContext[Server.Command],
    timeout: Timeout,
    ec: ExecutionContext,
  ): Route = {
    pathPrefix("provider") {
      get {
        parameters('zipCode, 'country.?) { (zipCode: String, country: Option[String]) =>
          val handler =
            SSEHandler(
              ProviderHandler.sse(
                zipCode,
                country,
                providerCoordinator,
              )
            )

          complete(handler)
        }
      }
    }
  }
}
