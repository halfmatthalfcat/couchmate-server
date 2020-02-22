package com.couchmate.api.routes

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout

import scala.concurrent.ExecutionContext

object MainRoutes {
  def apply()(
    implicit
    executionContext: ExecutionContext,
    timeout: Timeout,
  ): Route = {
    path("healthcheck") {
      complete("ok")
    }
  }
}
