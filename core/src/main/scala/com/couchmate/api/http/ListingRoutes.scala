package com.couchmate.api.http

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.couchmate.Server
import com.couchmate.api.PathUtils
import com.couchmate.common.dao.AiringDAO
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.grid.AiringConversion
import com.couchmate.services.ListingUpdater
import com.couchmate.services.ListingUpdater.StartJobRemoteResult
import com.couchmate.services.gracenote.listing.ListingPullType
import com.couchmate.util.akka.extensions.SingletonExtension
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ListingRoutes
  extends PlayJsonSupport
  with LazyLogging
  with PathUtils {

  def apply()(
    implicit
    ec: ExecutionContext,
    db: Database,
    system: ActorSystem[Nothing],
    config: Config,
    timeout: Timeout,
    singleton: SingletonExtension,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Route = concat(
    path("convert") {
      post {
        entity(as[Seq[AiringConversion]]) { conversions =>
          onComplete(AiringDAO.getAiringsFromGracenote(conversions)) {
            case Success(value) => complete(StatusCodes.OK -> value)
            case Failure(exception) =>
              logger.error(s"Couldn't get conversions: ${exception.getMessage}")
              complete(StatusCodes.InternalServerError)
          }
        }
      }
    },
    path("detail" / Segment) { airingId: String =>
      get {
        onComplete(AiringDAO.getShowFromAiring(airingId)) {
          case Success(value) => complete(StatusCodes.OK -> value)
          case Failure(exception) =>
            logger.error(s"Couldn't get show for ${airingId}: ${exception.getMessage}")
            complete(StatusCodes.InternalServerError)
        }
      }
    },
    path("job" / IntNumber.flatMap(ListingPullType.withValueOpt)) { jobType: ListingPullType =>
      get {
        onComplete(singleton
          .listingUpdater
          .ask(ref => ListingUpdater.StartJobRemote(jobType, ref))) {
          case Success(StartJobRemoteResult(queue)) => complete(StatusCodes.OK -> queue)
          case _ => complete(StatusCodes.InternalServerError)
        }
      }
    }
  )
}
