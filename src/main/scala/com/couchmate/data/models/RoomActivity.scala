package com.couchmate.data.models

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class RoomActivity(
  airingId: UUID,
  userId: UUID,
  action: RoomActivityType,
  created: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC")),
) extends Product with Serializable

object RoomActivity extends JsonConfig {
  implicit val format: OFormat[RoomActivity] = Json.format[RoomActivity]
}
