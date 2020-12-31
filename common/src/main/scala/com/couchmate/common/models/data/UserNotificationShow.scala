package com.couchmate.common.models.data

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserNotificationShow(
  userId: UUID,
  airingId: String
)

object UserNotificationShow {
  implicit val format: Format[UserNotificationShow] = Json.format[UserNotificationShow]
}
