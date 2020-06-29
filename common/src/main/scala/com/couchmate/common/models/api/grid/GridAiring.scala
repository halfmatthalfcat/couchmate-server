package com.couchmate.common.models.api.grid

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.models.data.RoomStatusType
import com.couchmate.common.util.slick.RowParser
import com.couchmate.common.db.PgProfile.plainAPI._
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class GridAiring(
  airingId: UUID,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  channelId: Long,
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
  status: RoomStatusType,
  count: Long,
)

object GridAiring {
  implicit val format: OFormat[GridAiring] = Json.format[GridAiring]
  implicit val rowParser: GetResult[GridAiring] = RowParser[GridAiring]
}
