package com.couchmate.data.models

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Airing(
  airingId: Option[UUID],
  showId: Long,
  startTime: OffsetDateTime,
  endTime: OffsetDateTime,
  duration: Int,
) extends Product with Serializable

object Airing extends JsonConfig {
  implicit val format: OFormat[Airing] = Json.format[Airing]
}
