package com.couchmate.common.models.thirdparty.gracenote

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class GracenoteChannelAiring(
  channel: String,
  callSign: String,
  affiliateCallSign: Option[String],
  affiliateId: Option[Long],
  stationId: Long,
  airings: Seq[GracenoteAiring],
)

object GracenoteChannelAiring {
  implicit val reads: Reads[GracenoteChannelAiring] = (
    (__ \ "channel").read[String] and
    (__ \ "callSign").read[String] and
    (__ \ "affiliateCallSign").readNullable[String] and
    (__ \ "affiliateId").readNullable[String]
                      .filter(_.exists(_ != "Independent"))
                      .map(_.map(_.toLong))
                      .orElse(Reads.pure(Option.empty)) and
    (__ \ "stationId").read[String].map(_.toLong) and
    (__ \ "airings").read[Seq[GracenoteAiring]]
  )(GracenoteChannelAiring.apply _)
}
