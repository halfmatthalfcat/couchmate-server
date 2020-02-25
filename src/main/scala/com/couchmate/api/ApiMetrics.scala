package com.couchmate.api

import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsSettings
import fr.davit.akka.http.metrics.prometheus.PrometheusRegistry
import io.prometheus.client.CollectorRegistry

trait ApiMetrics {
  val settings: HttpMetricsSettings =
    HttpMetricsSettings.default
       .withIncludeStatusDimension(true)
       .withIncludePathDimension(true)
  private[this] val prometheus: CollectorRegistry =
    CollectorRegistry.defaultRegistry
  val registry: PrometheusRegistry =
    PrometheusRegistry(settings, prometheus)
}
