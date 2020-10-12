package com.couchmate.api.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.api.ws.protocol._
import com.couchmate.api.ws.protocol.External._
import com.couchmate.common.dao.{UserActivityDAO, UserDAO, UserMetaDAO, UserPrivateDAO}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data._
import com.couchmate.util.akka.extensions.JwtExtension
import com.couchmate.util.jwt.Jwt.ExpiredJwtError
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object UserRoutes
  extends PlayJsonSupport
  with UserDAO
  with UserMetaDAO
  with UserPrivateDAO
  with UserActivityDAO
  with LazyLogging {

  def apply()(
    implicit
    ec: ExecutionContext,
    db: Database,
    jwt: JwtExtension
  ): Route = concat(
    path("register") {
      get {
        parameter(Symbol("token")) { token: String =>
          onComplete(for {
            claims <- Future.fromTry(jwt.validateToken(
              token,
              Map("scope" -> "register")
            )) recoverWith {
              case ExpiredJwtError => Future.failed(RegisterAccountError(
                RegisterAccountErrorCause.TokenExpired
              ))
              case _ => Future.failed(RegisterAccountError(
                RegisterAccountErrorCause.BadToken
              ))
            }
            userId = claims.userId
            email = claims.claims.getStringClaim("email")
            password = claims.claims.getStringClaim("password")
            user <- getUser(userId)
            _ <- user.fold[Future[User]](
              Future.failed(RegisterAccountError(
                RegisterAccountErrorCause.UserNotFound
              ))
            )(u => upsertUser(u.copy(
              role = UserRole.Registered,
              verified = true
            )))
            userMeta <- getUserMeta(userId)
            _ <- userMeta.fold[Future[UserMeta]](
              Future.failed(RegisterAccountError(
                RegisterAccountErrorCause.UnknownError
              ))
            )(uM => upsertUserMeta(uM.copy(
              userId = userId,
              email = Some(email)
            )))
            _ <- upsertUserPrivate(UserPrivate(
              userId = userId,
              password = password
            ))
            _ <- addUserActivity(UserActivity(
              userId = userId,
              action = UserActivityType.Registered,
              os = None,
              osVersion = None,
              brand = None,
              model = None
            ))
          } yield ()) {
            case Success(_) =>
              complete(200 -> Json.toJson[Protocol](VerifyAccountSuccessWeb))
            case Failure(RegisterAccountError(cause)) =>
              logger.error(s"Failed to register - $cause")
              complete(200 -> Json.toJson[Protocol](VerifyAccountFailed(cause)))
            case _ =>
              logger.error(s"Failed to register - Unknown")
              complete(200 -> Json.toJson[Protocol](VerifyAccountFailed(
                RegisterAccountErrorCause.UnknownError
              )))
          }
        }
      }
    },
    path("reset") {
      post {
        entity(as[Protocol]) {
          case ForgotPasswordReset(password, token) =>
            import com.github.t3hnar.bcrypt._

            onComplete(for {
              claims <- Future.fromTry(jwt.validateToken(
                token,
                Map("scope" -> "forgot")
              )) recoverWith {
                case ExpiredJwtError => Future.failed(ForgotPasswordError(
                  ForgotPasswordErrorCause.TokenExpired
                ))
                case _ => Future.failed(ForgotPasswordError(
                  ForgotPasswordErrorCause.BadToken
                ))
              }
              hashedPw <- Future.fromTry(password.bcryptSafe(10))
              _ <- upsertUserPrivate(UserPrivate(
                claims.userId,
                hashedPw
              ))
            } yield ()) {
              case Success(_) =>
                complete(200 -> Json.toJson[Protocol](ForgotPasswordResetSuccess))
              case Failure(ForgotPasswordError(cause)) =>
                complete(200 -> Json.toJson[Protocol](ForgotPasswordResetFailed(cause)))
              case _ =>
                complete(200 -> Json.toJson[Protocol](ForgotPasswordResetFailed(
                  ForgotPasswordErrorCause.Unknown
              )))
            }
          case _ => complete(StatusCodes.BadRequest)
        }
      }
    }
  )

}
