package com.couchmate.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.api._
import com.couchmate.data.models.{User, UserRole}

import scala.concurrent.Future

trait UserRoutes extends ApiFunctions {

  private[api] val userRoutes: Route =
    path("user") {
      pathEndOrSingleSlash {
        get {
          authenticate { (userId, _) =>
            async {
              db.user.getUser(userId) map {
                case Some(user) => StatusCodes.OK -> Some(user)
                case None => StatusCodes.InternalServerError -> None
              }
            }
          }
        } ~
        (post | put) {
          entity(as[User]) { user =>
            authenticate {
              authorize(
                // Admin privileges
                (_, userRole) => Future.successful(userRole == UserRole.Admin),
                // User owned
                (userId, _) => Future.successful(user.userId.contains(userId))
              ) {
                async {
                  db.user.upsertUser(user).map(StatusCodes.OK -> Some(_))
                }
              }
            }
          }
        }
      }
    }
}
