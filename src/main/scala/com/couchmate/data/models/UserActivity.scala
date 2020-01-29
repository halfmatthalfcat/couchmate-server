package com.couchmate.data.models

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class UserActivity(
  userId: UUID,
  action: UserActivityType,
  created: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC")),
) extends Product with Serializable

object UserActivity extends JsonConfig {
  implicit val format: OFormat[UserActivity] = Json.format[UserActivity]
  implicit val getResult: GetResult[UserActivity] = GenGetResult[UserActivity]
}
