package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class ChannelOwner(
  channelOwnerId: Option[Long],
  extId: Long,
  callsign: String
)

object ChannelOwner {
  implicit val format: Format[ChannelOwner] = Json.format[ChannelOwner]
}
