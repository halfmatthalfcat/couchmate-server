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
  originalAiringDate: Option[LocalDateTime],
  status: RoomStatusType,
  isNew: Boolean,
  count: Long,
  following: Long,
  sport: Option[GridSport],
  series: Option[GridSeries]
)

object GridAiringExtended {
  implicit val format: Format[GridAiringExtended] = Json.format[GridAiringExtended]
}