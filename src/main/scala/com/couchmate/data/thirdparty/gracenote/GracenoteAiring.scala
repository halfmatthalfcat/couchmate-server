package com.couchmate.data.thirdparty.gracenote

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime}

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class GracenoteAiring(
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  program: GracenoteProgram,
)

object GracenoteAiring {
  implicit val reads: Reads[GracenoteAiring] = (
    (__ \ "startTime").read[String].map(LocalDateTime.parse(_, DateTimeFormatter.ISO_OFFSET_DATE_TIME)) and
    (__ \ "endTime").read[String].map(LocalDateTime.parse(_, DateTimeFormatter.ISO_OFFSET_DATE_TIME)) and
    (__ \ "duration").read[Int] and
    (__ \ "program").read[GracenoteProgram]
  )(GracenoteAiring.apply _)

  implicit val writes: OWrites[GracenoteAiring] = Json.writes[GracenoteAiring]
}
