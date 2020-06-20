package com.couchmate.api.models.grid

import play.api.libs.json.{Format, Json}

case class GridChannel(
  channelId: Long,
  channelNumber: String,
  callsign: String,
  airings: Seq[GridAiring]
)

object GridChannel {
  implicit val format: Format[GridChannel] = Json.format[GridChannel]
}