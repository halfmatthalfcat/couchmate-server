package com.couchmate.api.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.data.schema.PgProfile.api._
import com.couchmate.services.thirdparty.gracenote.{GracenoteService, ProviderIngestor}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext

object ProviderRoutes extends PlayJsonSupport {
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  def apply()(
    implicit
    actorSystem: ActorSystem[Nothing],
    timeout: Timeout,
    db: Database,
    ec: ExecutionContext,
    gracenoteService: GracenoteService,
  ): Route = {
    pathPrefix("provider") {
      pathEndOrSingleSlash {
        parameters(Symbol("zipCode").as[String], Symbol("country").as[String].?) {
          (zipCode: String, country: Option[String]) =>
            val providers = ProviderIngestor.ingestProviders(
              zipCode,
              country.getOrElse("USA"),
            )

            complete(providers)
        }
      }
    }
  }

}
