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

  private[this] val registered: Counter = Counter
    .build("cm_registered", "Counter of accounts registered")
    .labelNames("tz", "country")
    .register(collector)

  private[this] val reacted: Counter = Counter
    .build("cm_reaction", "Counter of reactions to messages")
    .register(collector)

  private[this] val userBlocked: Counter = Counter
    .build("cm_user_blocked", "Counter of users blocked")
    .register(collector)

  private[this] val userReported: Counter = Counter
    .build("cm_user_reported", "Counter of users reported")
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

  def incReaction(): Unit =
    reacted.inc()

  def incBlocked(): Unit =
    userBlocked.inc()

  def incReported(): Unit =
    userReported.inc()

  def incRegistered(
    tz: String,
    country: Option[CountryCode]
  ): Unit = registered
    .labels(
      tz,
      country.flatMap(c => Option(c.getAlpha3)).getOrElse("N/A")
    ).inc()

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
      country.flatMap(c => Option(c.getAlpha3)).getOrElse("N/A")
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
      country.flatMap(c => Option(c.getAlpha3)).getOrElse("N/A")
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
      country.flatMap(c => Option(c.getAlpha3)).getOrElse("N/A")
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
      country.flatMap(c => Option(c.getAlpha3)).getOrElse("N/A")
    ).dec()

  def startTimeInRoom(): Histogram.Timer =
    timeInRoom.startTimer()

}
