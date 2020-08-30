package com.couchmate.common.models.data

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID

import play.api.libs.json.{Format, Json}

case class RoomActivity(
  airingId: String,
  userId: UUID,
  action: RoomActivityType,
  created: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC")),
)

object RoomActivity extends JsonConfig {
  implicit val format: Format[RoomActivity] = Json.format[RoomActivity]
}
