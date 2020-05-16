package com.couchmate.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.server.Directives._
import com.couchmate.api.ApiFunctions
import com.couchmate.api.models.signup.{AnonSignup, EmailSignup, SignupError}
import com.couchmate.data.models.CMError
import com.couchmate.services.SignupService

trait SignupRoutes
  extends ApiFunctions
  with SignupService {

  private[this] implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case err: CMError[SignupError] =>
        complete(StatusCodes.BadRequest -> err)
      case _ =>
        complete(StatusCodes.InternalServerError)
    }

  private[api] val signupRoutes: Route = Route.seal {
    pathPrefix("signup") {
      concat(
        path("anon") {
          post {
            cors() {
              asyncWithEntity { signup: AnonSignup =>
                anonSignup(signup)
                  .map(StatusCodes.OK -> Some(_))
              }
            }
          }
        },
        path("email") {
          post {
            asyncWithEntity { signup: EmailSignup =>
              emailSignup(signup)
                .map(StatusCodes.OK -> Some(_))
            }
          }
        },
        path("validate") {
          parameters(Symbol("email"), Symbol("username")) { (email: String, username: String) =>
            async {
              validateSignup(email, username)
                .map(StatusCodes.OK -> Some(_))
            }
          }
        }
      )
    }
  }
}
