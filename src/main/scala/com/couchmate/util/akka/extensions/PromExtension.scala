package com.couchmate.util.akka.extensions

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.couchmate.util.metrics.PrometheusMetrics
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsSettings
import fr.davit.akka.http.metrics.prometheus.PrometheusRegistry
import io.prometheus.client.CollectorRegistry

class PromExtension(system: ActorSystem[_])
  extends Extension
  with PrometheusMetrics {
  val settings: HttpMetricsSettings =
    HttpMetricsSettings.default
                       .withIncludeStatusDimension(true)
                       .withIncludePathDimension(true)
  val registry: PrometheusRegistry =
    PrometheusRegistry(settings, collector)
}

object PromExtension extends ExtensionId[PromExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): PromExtension = new PromExtension(system)
}
