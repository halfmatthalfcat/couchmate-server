package com.couchmate.util.metrics

import com.neovisionaries.i18n.CountryCode
import io.prometheus.client.{CollectorRegistry, Counter}

trait PrometheusMetrics {
  protected val collector: CollectorRegistry =
    CollectorRegistry.defaultRegistry

  private[this] val session: Counter = Counter
    .build("sessions", "Counter of created sessions")
    .labelNames("provider_id", "provider", "tz", "country")
    .register(collector)

  def incSession(
    providerId: Long,
    provider: String,
    tz: String,
    country: Option[CountryCode]
  ): Unit = session
    .labels(
      providerId.toString,
      provider,
      tz,
      country.map(_.getAlpha3).getOrElse("N/A")
    ).inc()

}
