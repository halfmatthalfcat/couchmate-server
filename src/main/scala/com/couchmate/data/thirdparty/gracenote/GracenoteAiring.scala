package com.couchmate.data.thirdparty.gracenote

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime}

import com.couchmate.util.DateUtils
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
    (__ \ "startTime").read[String]
                      .map(DateUtils.toLocalDateTime(
                        DateTimeFormatter.ISO_INSTANT,
                        DateTimeFormatter.ofPattern("u-MM-dd'T'HH:mmX"),
                        DateTimeFormatter.ofPattern("u-MM-dd'T'HH:mm"),
                        DateTimeFormatter.ofPattern("u-MM-dd'T'HH:mm:ss"),
                      )) and
    (__ \ "endTime").read[String]
                    .map(DateUtils.toLocalDateTime(
                      DateTimeFormatter.ISO_INSTANT,
                      DateTimeFormatter.ofPattern("u-MM-dd'T'HH:mmX"),
                      DateTimeFormatter.ofPattern("u-MM-dd'T'HH:mm"),
                      DateTimeFormatter.ofPattern("u-MM-dd'T'HH:mm:ss"),
                    )) and
    (__ \ "duration").read[Int] and
    (__ \ "program").read[GracenoteProgram]
  )(GracenoteAiring.apply _)

  implicit val writes: OWrites[GracenoteAiring] = Json.writes[GracenoteAiring]
}
