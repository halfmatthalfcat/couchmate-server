package com.couchmate.common.models.data

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserNotificationSeries(
  userId: UUID,
  seriesId: Long,
  providerChannelId: Long,
  name: String,
  callsign: String,
  hash: String,
  onlyNew: Boolean = true,
  active: Boolean = true,
  created: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC"))
)

object UserNotificationSeries {
  implicit val format: Format[UserNotificationSeries] = Json.format[UserNotificationSeries]
}
