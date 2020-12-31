package com.couchmate.common.models.data

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserNotificationTeam(
  userId: UUID,
  teamId: Long
)

object UserNotificationTeam {
  implicit val format: Format[UserNotificationTeam] = Json.format[UserNotificationTeam]
}