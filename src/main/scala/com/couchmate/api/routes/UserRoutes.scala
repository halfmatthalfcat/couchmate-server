package com.couchmate.api.routes

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import com.couchmate.api._
import com.couchmate.data.models.User
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.{ExecutionContext, Future}

object UserRoutes extends PlayJsonSupport {
  def apply()(
    implicit
    executionContext: ExecutionContext,
    timeout: Timeout,
  ): Route = {
    path("user") {
      (post | put) {
        pathEndOrSingleSlash {
          entity(as[User]) { user =>
            asyncWithBody[User] {
              Future.successful(Left(200 -> user))
            }
          }
        }
      }
    }
  }
}
