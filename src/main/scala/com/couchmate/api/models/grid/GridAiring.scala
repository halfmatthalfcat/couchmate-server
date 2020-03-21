package com.couchmate.api.models.grid

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.util.slick.RowParser
import com.couchmate.data.db.PgProfile.plainAPI._
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class GridAiring(
  airingId: UUID,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  channel: String,
  callsign: String,
  title: String,
  description: String,
  `type`: String,
  seriesTitle: Option[String],
  sportTitle: Option[String],
  episode: Option[Long],
  season: Option[Long],
  originalAiringDate: Option[LocalDateTime],
)

object GridAiring {
  implicit val format: OFormat[GridAiring] = Json.format[GridAiring]
  implicit val rowParser: GetResult[GridAiring] = RowParser[GridAiring]
}
