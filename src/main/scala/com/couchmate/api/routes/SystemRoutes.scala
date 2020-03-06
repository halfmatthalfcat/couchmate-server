package com.couchmate.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import com.couchmate.api.{ApiFunctions, ApiMetrics}
import com.typesafe.scalalogging.LazyLogging
import fr.davit.akka.http.metrics.prometheus.PrometheusRegistry
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers

trait SystemRoutes
  extends ApiFunctions
  with PrometheusMarshallers {

  private[api] def systemRoutes(
    registry: PrometheusRegistry,
  ): Route =
    path("healthcheck") {
      complete(StatusCodes.OK)
    } ~
    path("metrics") {
      get {
        metrics(registry)
      }
    }
}
