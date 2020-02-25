package com.couchmate.api

import java.util.UUID

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.data.db.CMDatabase
import com.couchmate.data.models.UserRole
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives
import play.api.libs.json.{Reads, Writes}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait ApiFunctions
  extends PlayJsonSupport
  with JwtProvider
  with HttpMetricsDirectives {
  implicit val ec: ExecutionContext
  val db: CMDatabase

  /**
   * Authenticates an incoming request
   * - Validates the JWT
   * - Gets the UUID from the subject
   * - Pulls the UserMeta and services UUID/UserRole to inner route
   *
   * @param block A function that takes a UUID and UserRole, returns a route
   * @return A completed [[Route]]
   */
  private[api] def authenticate(block: (UUID, UserRole) => Route): Route = {
    val bearerRegex = "Bearer (.+)".r
    optionalHeaderValueByName("Authorization") {
      case Some(value) if bearerRegex.matches(value) =>
        val jwt = bearerRegex.findFirstMatchIn(value).get.group(1)
        validate(jwt) match {
          case Success(uuid) =>
            onComplete(db.userMeta.getUserMeta(uuid)) {
              case Success(Some(meta)) => block(uuid, meta.role)
              case Failure(_) => complete(StatusCodes.InternalServerError)
            }
          case _ => complete(StatusCodes.Unauthorized)
        }
      case _ => complete(StatusCodes.Unauthorized)
    }
  }

  type AuthorizationRule = (UUID, UserRole) => Future[Boolean]

  /**
   * Fail fast, sequential rule processor
   * Once a falsey value is found, ignore the rest of the futures and return false
   *
   * @param userId The userId requesting the resource
   * @param userRole The role of the user requesting the resource
   * @param rules A Seq of Futures that return a Boolean
   * @return A future of Boolean, whether the rules pass or fail
   */
  private[this] def processRules(userId: UUID, userRole: UserRole, rules: Seq[AuthorizationRule]): Future[Boolean] = {
    rules.foldLeft(Future.successful(true)) {
      case (acc, rule) => acc flatMap { prevSuccess =>
        if (!prevSuccess) Future.successful(prevSuccess)
        else rule(userId, userRole)
      }
    }
  }

  /**
   * Authorize a particular route after authentication
   *
   * @param rules A Seq of rules that authorizes a user for a resource
   * @param block  The inner route
   * @param userId The userId requesting the resource
   * @param userRole The role of the user requesting the resource
   * @return A completed [[Route]]
   */
  private[api] def authorize(rules: AuthorizationRule*)(block: => Route)(
    userId: UUID,
    userRole: UserRole,
  ): Route = {
    onComplete(processRules(userId, userRole, rules)) {
      case Success(true) => block
      case Success(false) => complete(StatusCodes.Forbidden)
      case Failure(_) => complete(StatusCodes.InternalServerError)
    }
  }

  private[api] def async[W: Writes](block: => Future[(StatusCode, Option[W])]): Route = {
    onComplete(block) {
      case Success((code, Some(body))) =>
        complete(code -> body)
      case Success((code, None)) =>
        complete(code)
      case Failure(_) =>
        complete(StatusCodes.InternalServerError)
    }
  }

  private[api] def asyncWithEntity[R: Reads, W: Writes](block: R => Future[(StatusCode, Option[W])]): Route = {
    entity(as[R]) { entity =>
      onComplete(block(entity)) {
        case Success((code, Some(body))) =>
          complete(code -> body)
        case Success((code, None)) =>
          complete(code)
        case Failure(_) =>
          complete(StatusCodes.InternalServerError)
      }
    }
  }
}
