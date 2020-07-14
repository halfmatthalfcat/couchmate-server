package com.couchmate.util.metrics

import com.neovisionaries.i18n.CountryCode
import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram}

trait PrometheusMetrics {
  protected val collector: CollectorRegistry =
    CollectorRegistry.defaultRegistry

  private[this] val rooms: Counter = Counter
    .build("cm_rooms", "Counter of created rooms")
    .register(collector)

  private[this] val messages: Counter = Counter
    .build("cm_messages", "Counter of messages sent")
    .register(collector)

  private[this] val sessions: Gauge = Gauge
    .build("cm_sessions", "Current number of sessions")
    .labelNames("provider_id", "provider", "tz", "country")
    .register(collector)

  private[this] val attendance: Gauge = Gauge
    .build("cm_attendance", "Current room attendance")
    .labelNames("provider_id", "provider", "tz", "country")
    .register(collector)

  private[this] val timeInRoom: Histogram = Histogram
    .build("cm_time_in_room", "Summary of time spent in a room in seconds")
    .buckets(1d, 10d, 30d, 60d, 90d, 300d, 600d, 1800d, 3600d, 7200d)
    .register(collector)

  def incRoom(): Unit =
    rooms.inc()

  def incMessages(): Unit =
    messages.inc()

  def incSession(
    providerId: Long,
    provider: String,
    tz: String,
    country: Option[CountryCode]
  ): Unit = sessions
    .labels(
      providerId.toString,
      provider,
      tz,
      country.map(_.getAlpha3).getOrElse("N/A")
    ).inc()

  def decSession(
    providerId: Long,
    provider: String,
    tz: String,
    country: Option[CountryCode]
  ): Unit = sessions
    .labels(
      providerId.toString,
      provider,
      tz,
      country.map(_.getAlpha3).getOrElse("N/A")
    ).dec()

  def incAttendance(
    providerId: Long,
    provider: String,
    tz: String,
    country: Option[CountryCode]
  ): Unit = attendance
    .labels(
      providerId.toString,
      provider,
      tz,
      country.map(_.getAlpha3).getOrElse("N/A")
    ).inc()

  def decAttendance(
    providerId: Long,
    provider: String,
    tz: String,
    country: Option[CountryCode]
  ): Unit = attendance
    .labels(
      providerId.toString,
      provider,
      tz,
      country.map(_.getAlpha3).getOrElse("N/A")
    ).dec()

  def startTimeInRoom(): Histogram.Timer =
    timeInRoom.startTimer()

}
