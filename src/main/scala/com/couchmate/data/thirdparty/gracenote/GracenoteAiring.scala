package com.couchmate.data.thirdparty.gracenote

import java.time.OffsetDateTime
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class GracenoteAiring(
  startTime: OffsetDateTime,
  endTime: OffsetDateTime,
  duration: Int,
  program: GracenoteProgram,
)

object GracenoteAiring {
  implicit val reads: Reads[GracenoteAiring] = (
    (__ \ "startTime").read[String].map(OffsetDateTime.parse(_)) and
    (__ \ "endTime").read[String].map(OffsetDateTime.parse(_)) and
    (__ \ "duration").read[Int] and
    (__ \ "program").read[GracenoteProgram]
  )(GracenoteAiring.apply _)

  implicit val writes: OWrites[GracenoteAiring] = Json.writes[GracenoteAiring]
}
