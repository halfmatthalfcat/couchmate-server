package com.couchmate.common.models.api.grid

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.models.data.RoomStatusType
import com.couchmate.common.util.slick.RowParser
import com.couchmate.common.db.PgProfile.plainAPI._
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class GridAiring(
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
  isNew: Boolean,
  sportEventId: Option[Long],
  episodeId: Option[Long],
  originalAiringDate: Option[LocalDateTime]
) {
  def toExtended(
    series: Option[GridSeries],
    sport: Option[GridSport]
  ): GridAiringExtended = GridAiringExtended(
    airingId = this.airingId,
    startTime = this.startTime,
    endTime = this.endTime,
    duration = this.duration,
    channelId = this.channelId,
    channel = this.channel,
    callsign = this.callsign,
    title = this.title,
    description = this.description,
    `type` = this.`type`,
    originalAiringDate = this.originalAiringDate,
    sport = sport,
    series = series,
    isNew = isNew
  )
}

object GridAiring {
  implicit val format: OFormat[GridAiring] = Json.format[GridAiring]
  implicit val rowParser: GetResult[GridAiring] = RowParser[GridAiring]
}
