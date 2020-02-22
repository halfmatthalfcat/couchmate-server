package com.couchmate.api.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.data.db._
import com.couchmate.data.models.{Provider, ProviderOwner}
import com.couchmate.services.thirdparty.gracenote.provider.ProviderIngestor
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
            // complete(database.withTx(database.provider.upsertProvider(provider)))
            complete(provider)
          }
        } ~
        get {
          parameters('zipCode, 'country.?) { (zipCode: String, country: Option[String]) =>
            val stream = ingestor.ingestProviders(
              zipCode, country.orElse(Some("USA")),
            )

            complete(stream)
          }
        }
      } ~
      pathPrefix("owner") {
        pathEndOrSingleSlash {
          post {
            entity(as[ProviderOwner]) { providerOwner =>
              // complete(database.withTx(database.providerOwner.upsertProviderOwner(providerOwner)))
              complete(providerOwner)
            }
          }
        }
      }
    }
  }
}
