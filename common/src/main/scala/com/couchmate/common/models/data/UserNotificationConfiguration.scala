package com.couchmate.common.models.data

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserNotificationConfiguration(
  userId: UUID,
  active: Boolean,
  platform: ApplicationPlatform,
  token: Option[String],
  deviceId: Option[String]
)

object UserNotificationConfiguration {
  implicit val format: Format[UserNotificationConfiguration] = Json.format[UserNotificationConfiguration]
}