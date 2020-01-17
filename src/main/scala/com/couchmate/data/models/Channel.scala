package com.couchmate.data.models

import play.api.libs.json.{Json, OFormat}

case class Channel(
  channelId: Option[Long],
  sourceId: Long,
  extId: Long,
  callsign: String,
) extends Product with Serializable

object Channel extends JsonConfig {
  implicit val format: OFormat[Channel] = Json.format[Channel]
}
