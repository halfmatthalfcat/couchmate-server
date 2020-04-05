package com.couchmate.api

import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.javadsl.model.HttpHeaderRange
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.couchmate.api.routes._
import fr.davit.akka.http.metrics.prometheus.PrometheusRegistry

trait Routes
  extends ApiFunctions
  with SystemRoutes
  with ListingRoutes
  with ProviderRoutes
  with UserRoutes
  with SignupRoutes {

  def routes(
    registry: PrometheusRegistry,
  ): Route = {
    systemRoutes(registry) ~
    pathPrefix("api") {
      cors() {
        userRoutes ~
        providerRoutes ~
        listingRoutes ~
        signupRoutes
      }
    }
  }
}
