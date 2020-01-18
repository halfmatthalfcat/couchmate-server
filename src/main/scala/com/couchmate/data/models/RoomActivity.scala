package com.couchmate.data.models

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class RoomActivity(
  airingId: UUID,
  userId: UUID,
  action: RoomActivityType,
  created: OffsetDateTime = OffsetDateTime.now(),
) extends Product with Serializable

object RoomActivity extends JsonConfig {
  val format: OFormat[RoomActivity] = Json.format[RoomActivity]
}
