package com.couchmate.data.models

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class UserActivity(
  userId: UUID,
  action: UserActivityType,
  created: OffsetDateTime = OffsetDateTime.now(),
) extends Product with Serializable

object UserActivity extends JsonConfig {
  implicit val format: OFormat[UserActivity] = Json.format[UserActivity]
}
