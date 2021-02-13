package com.couchmate.common.models.data

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserNotificationTeam(
  userId: UUID,
  teamId: Long,
  providerId: Long,
  name: String,
  hash: String,
  onlyNew: Boolean = true,
  active: Boolean = true,
  created: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC"))
)

object UserNotificationTeam {
  implicit val format: Format[UserNotificationTeam] = Json.format[UserNotificationTeam]
}