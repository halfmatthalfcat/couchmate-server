package com.couchmate.external.gracenote.models

import java.time.LocalDateTime

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class GracenoteChannelAiring(
  providerId: Option[Long],
  channel: String,
  callSign: String,
  affiliateCallSign: Option[String],
  stationId: Long,
  startDate: Option[LocalDateTime],
  airings: Seq[GracenoteAiring],
)

object GracenoteChannelAiring {
  implicit val reads: Reads[GracenoteChannelAiring] = (
    (__ \ "providerId").readNullable[Long] and
    (__ \ "channel").read[String] and
    (__ \ "callSign").read[String] and
    (__ \ "affiliateCallSign").readNullable[String] and
    (__ \ "stationId").read[String].map(_.toLong) and
    (__ \ "startDate").readNullable[String].map(_.map(LocalDateTime.parse(_))) and
    (__ \ "airings").read[Seq[GracenoteAiring]]
  )(GracenoteChannelAiring.apply _)

  implicit val writes: OWrites[GracenoteChannelAiring] = Json.writes[GracenoteChannelAiring]
}
