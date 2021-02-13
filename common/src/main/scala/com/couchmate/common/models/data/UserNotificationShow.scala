package com.couchmate.common.models.data

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserNotificationShow(
  userId: UUID,
  airingId: String,
  providerChannelId: Long,
  name: String,
  callsign: String,
  hash: String,
  onlyNew: Boolean = true,
  active: Boolean = true,
  created: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC"))
)

object UserNotificationShow {
  implicit val format: Format[UserNotificationShow] = Json.format[UserNotificationShow]
}
