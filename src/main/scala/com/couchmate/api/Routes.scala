package com.couchmate.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.couchmate.api.routes._
import com.couchmate.util.AmqpProvider
import fr.davit.akka.http.metrics.prometheus.PrometheusRegistry

trait Routes
  extends ApiFunctions
    with SystemRoutes
    with ListingRoutes
    with ProviderRoutes
    with UserRoutes
    with SignupRoutes
    with RoomRoutes {

  def routes(
    registry: PrometheusRegistry,
  ): Route = {
    systemRoutes(registry) ~
    pathPrefix("api") {
      cors() {
        concat(
          roomRoutes,
          userRoutes,
          providerRoutes,
          listingRoutes,
          signupRoutes,
        )
      }
    }
  }
}
