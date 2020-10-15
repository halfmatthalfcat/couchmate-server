package com.couchmate.api

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import com.couchmate.Server
import com.couchmate.api.http.{ListingRoutes, UserRoutes}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.services.user.commands.UserActions
import com.couchmate.services.user.context.GeoContext
import com.couchmate.util.akka.WSPersistentActor
import com.couchmate.util.akka.extensions.{JwtExtension, UserExtension}
import com.typesafe.config.Config
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives
import fr.davit.akka.http.metrics.prometheus.PrometheusRegistry
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

trait Routes
  extends CorsDirectives
  with HttpMetricsDirectives {
  implicit val ec: ExecutionContext
  implicit val ctx: ActorContext[Server.Command]

  def routes(
    registry: PrometheusRegistry,
  )(
    implicit
    db: Database,
    jwt: JwtExtension,
    user: UserExtension,
    config: Config,
    ctx: ActorContext[Server.Command]
  ): Route = cors() {
    concat(
      path("metrics") {
        get {
          metrics(registry)
        }
      },
      path("ws") {
        complete(StatusCodes.Gone -> "v1 WS endpoint deprecated, use /v2/ws")
      },
      pathPrefix("v2") {
        path("ws") {
          parameters(
            Symbol("token").as[Option[String]],
            Symbol("tz").as[String],
            Symbol("locale").as[String],
            Symbol("region").as[String],
          ) { case (token, tz, locale, region) =>
            onComplete(UserActions.getOrCreateUser(token, GeoContext(
              locale, tz, region,
            ))) {
              case Success(userId) => handleWebSocketMessages(
                WSPersistentActor(userId, GeoContext(
                  locale, tz, region,
                ), user, ctx)
              )
              case Failure(ex) =>
                ctx.log.error(s"Failed to getOrCreate user: $token", ex)
                complete(StatusCodes.InternalServerError)
            }
          }
        }
      },
      UserRoutes(),
      pathPrefix("api") {
        withApiKey(
          pathPrefix("listing")(ListingRoutes())
        )
      }
    )
  }
}
