package com.couchmate.api.http

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.api.ws.WSClient.upsertUserPrivate
import com.couchmate.api.ws.protocol.{ForgotPasswordError, ForgotPasswordErrorCause, ForgotPasswordReset, ForgotPasswordResetFailed, ForgotPasswordResetSuccess, Protocol, RegisterAccountError, RegisterAccountErrorCause, ResetPasswordFailed, ResetPasswordSuccess, VerifyAccountFailed, VerifyAccountSuccess}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.dao.{UserDAO, UserPrivateDAO}
import com.couchmate.common.models.data.{User, UserPrivate}
import com.couchmate.util.akka.extensions.JwtExtension
import com.couchmate.util.jwt.Jwt.ExpiredJwtError
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object UserRoutes
  extends PlayJsonSupport
  with UserDAO
  with UserPrivateDAO {

  def apply()(
    implicit
    ec: ExecutionContext,
    db: Database,
    jwt: JwtExtension
  ): Route = concat(
    path("register") {
      get {
        parameter(Symbol("token")) { token =>
          onComplete(for {
            userId <- Future.fromTry[UUID](jwt.validateToken(
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
            user <- getUser(userId)
            _ <- user.fold[Future[User]](
              Future.failed(RegisterAccountError(
                RegisterAccountErrorCause.UserNotFound
              ))
            )(u => upsertUser(u.copy(
              verified = true
            )))
          } yield ()) {
            case Success(_) =>
              complete(200 -> Json.toJson[Protocol](VerifyAccountSuccess))
            case Failure(RegisterAccountError(cause)) =>
              complete(200 -> Json.toJson[Protocol](VerifyAccountFailed(cause)))
            case _ =>
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
              userId <- Future.fromTry(jwt.validateToken(
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
                userId,
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
