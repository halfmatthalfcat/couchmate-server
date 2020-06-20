package com.couchmate.data.models

import java.time.LocalDateTime
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Airing(
  airingId: Option[UUID],
  showId: Long,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
)

object Airing extends JsonConfig {
  implicit val format: OFormat[Airing] = Json.format[Airing]
}
