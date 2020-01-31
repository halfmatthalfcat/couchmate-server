package com.couchmate.common.models

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class UserActivity(
  userId: UUID,
  action: UserActivityType,
  created: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC")),
) extends Product with Serializable

object UserActivity extends JsonConfig {
  implicit val format: OFormat[UserActivity] = Json.format[UserActivity]
}
