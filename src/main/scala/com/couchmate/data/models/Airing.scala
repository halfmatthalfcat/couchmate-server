package com.couchmate.data.models

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class Airing(
  airingId: Option[UUID],
  showId: Long,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
) extends Product with Serializable

object Airing extends JsonConfig {
  implicit val format: OFormat[Airing] = Json.format[Airing]
  implicit val getResult: GetResult[Airing] = GenGetResult[Airing]
}
