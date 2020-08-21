package com.couchmate.common.models.data

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserActivity(
  userId: UUID,
  action: UserActivityType,
  created: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC")),
  os: Option[String],
  osVersion: Option[String],
  brand: Option[String],
  model: Option[String]
)

object UserActivity extends JsonConfig {
  implicit val format: Format[UserActivity] = Json.format[UserActivity]
}
