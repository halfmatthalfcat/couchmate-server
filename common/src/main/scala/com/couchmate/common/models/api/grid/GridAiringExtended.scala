package com.couchmate.common.models.api.grid

import java.time.LocalDateTime

import com.couchmate.common.models.data.RoomStatusType
import play.api.libs.json.{Format, Json}

case class GridAiringExtended(
  airingId: String,
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
  teams: Seq[GridSportTeam],
  episode: Option[Long],
  season: Option[Long],
  originalAiringDate: Option[LocalDateTime],
  status: RoomStatusType,
  count: Long
)

object GridAiringExtended {
  implicit val format: Format[GridAiringExtended] = Json.format[GridAiringExtended]


}