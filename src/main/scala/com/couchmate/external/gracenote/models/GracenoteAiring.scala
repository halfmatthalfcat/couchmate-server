package com.couchmate.external.gracenote.models

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.couchmate.util.DateUtils
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class GracenoteAiring(
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  program: GracenoteProgram,
) {
  override def equals(obj: Any): Boolean = obj match {
    case GracenoteAiring(startTime, endTime, _, program) => (
      program.rootId == this.program.rootId &&
      startTime.isEqual(this.startTime) &&
      endTime.isEqual(this.endTime)
    )
    case _ => super.equals(obj)
  }
}

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
