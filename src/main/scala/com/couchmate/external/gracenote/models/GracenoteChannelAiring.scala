package com.couchmate.external.gracenote.models

import play.api.libs.json._

case class GracenoteChannelAiring(
  channel: String,
  callSign: String,
  affiliateCallSign: Option[String],
  stationId: Long,
  airings: Seq[GracenoteAiring],
)

object GracenoteChannelAiring {
  implicit val format: Format[GracenoteChannelAiring] = Json.format[GracenoteChannelAiring]
}
