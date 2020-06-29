package com.couchmate.common.models.data

import java.time.LocalDateTime
import java.util.UUID

import play.api.libs.json.{Format, Json}

case class Airing(
  airingId: Option[UUID],
  showId: Long,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
)

object Airing extends JsonConfig {
  implicit val format: Format[Airing] = Json.format[Airing]
}
