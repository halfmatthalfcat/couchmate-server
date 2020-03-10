package com.couchmate.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import com.couchmate.api.ApiFunctions
import com.couchmate.api.models.signup.{AnonSignup, EmailSignup}
import com.couchmate.services.SignupService

trait SignupRoutes
  extends ApiFunctions
  with SignupService {

  private[api] val signupRoutes: Route =
    pathPrefix("signup") {
      path("anon") {
        asyncWithEntity { signup: AnonSignup =>
          anonSignup(signup)
            .map(StatusCodes.OK -> Some(_))
        }
      } ~
      path("email") {
        asyncWithEntity { signup: EmailSignup =>
          emailSignup(signup)
            .map(StatusCodes.OK -> Some(_))
        }
      }
    }
}
