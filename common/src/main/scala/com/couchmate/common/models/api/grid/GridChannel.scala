package com.couchmate.common.models.api.grid

import play.api.libs.json.{Format, Json}

case class GridChannel(
  channelId: Long,
  channelNumber: String,
  callsign: String,
  airings: Seq[GridAiringExtended]
)

object GridChannel {
  implicit val format: Format[GridChannel] = Json.format[GridChannel]
}