package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class Channel(
  channelId: Option[Long],
  extId: Long,
  channelOwnerId: Option[Long],
  callsign: String,
) extends Product with Serializable

object Channel extends JsonConfig {
  implicit val format: Format[Channel] = Json.format[Channel]
}
