package com.couchmate.api.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.common.models.{Provider, ProviderOwner}
import com.couchmate.data.db.{ProviderDAO, ProviderOwnerDAO}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext

object ProviderRoutes extends PlayJsonSupport {
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  def apply(
    providerDAO: ProviderDAO,
    providerOwnerDAO: ProviderOwnerDAO,
  )(
    implicit
    actorSystem: ActorSystem[Nothing],
    timeout: Timeout,
    ec: ExecutionContext,
  ): Route = {
    pathPrefix("provider") {
      pathEndOrSingleSlash {
        post {
          entity(as[Provider]) { provider =>
            complete(providerDAO.upsertProvider(provider))
          }
        }
      } ~
      pathPrefix("owner") {
        pathEndOrSingleSlash {
          post {
            entity(as[ProviderOwner]) { providerOwner =>
              complete(providerOwnerDAO.upsertProviderOwner(providerOwner))
            }
          }
        }
      }
    }
  }
}
