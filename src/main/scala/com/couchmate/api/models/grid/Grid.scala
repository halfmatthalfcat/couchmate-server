package com.couchmate.api.models.grid

import java.time.LocalDateTime

import play.api.libs.json.{Json, OFormat}

case class Grid(
  providerId: Long,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  airings: Seq[GridAiring],
) extends Product with Serializable

object Grid {
  implicit val format: OFormat[Grid] = Json.format[Grid]
}
