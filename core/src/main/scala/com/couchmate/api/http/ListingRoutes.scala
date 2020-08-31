package com.couchmate.api.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.common.dao.AiringDAO
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.grid.AiringConversion
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ListingRoutes
  extends PlayJsonSupport
  with LazyLogging
  with AiringDAO {

  def apply()(
    implicit
    ec: ExecutionContext,
    db: Database,
    config: Config
  ): Route = concat(
    path("convert") {
      post {
        entity(as[Seq[AiringConversion]]) { conversions =>
          onComplete(getAiringsFromGracenote(conversions)) {
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
        onComplete(getShowFromAiring(airingId)) {
          case Success(value) => complete(StatusCodes.OK -> value)
          case Failure(exception) =>
            logger.error(s"Couldn't get show for ${airingId}: ${exception.getMessage}")
            complete(StatusCodes.InternalServerError)
        }
      }
    }
  )
}
