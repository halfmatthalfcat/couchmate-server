package com.couchmate.api.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.common.models.{Provider, ProviderOwner}
import com.couchmate.data.db.{CMContext, CMDatabase, ProviderDAO, ProviderOwnerDAO}
import com.couchmate.services.thirdparty.gracenote.ProviderIngestor
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext

object ProviderRoutes
  extends PlayJsonSupport {
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  def apply(
    database: CMDatabase,
    ingestor: ProviderIngestor,
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
            complete(database.provider.upsertProvider(provider))
          }
        } ~
        get {
          parameters('zipCode, 'country.?) {
            
          }
        }
      } ~
      pathPrefix("owner") {
        pathEndOrSingleSlash {
          post {
            entity(as[ProviderOwner]) { providerOwner =>
              complete(database.providerOwner.upsertProviderOwner(providerOwner))
            }
          }
        }
      }
    }
  }
}
