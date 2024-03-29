package com.couchmate.common.models.thirdparty.gracenote

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.couchmate.common.util.DateUtils
import com.couchmate.common.util.slick.RowParser
import play.api.libs.functional.syntax._
import play.api.libs.json._
import slick.jdbc.GetResult

case class GracenoteAiring(
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  qualifiers: Seq[String],
  program: GracenoteProgram,
) {
  override def equals(obj: Any): Boolean = obj match {
    case GracenoteAiring(startTime, endTime, _, _, program) => (
      program.rootId == this.program.rootId &&
      startTime.isEqual(this.startTime) &&
      endTime.isEqual(this.endTime)
    )
    case _ => super.equals(obj)
  }

  def isNew: Boolean =
    qualifiers.contains("New") &&
    qualifiers.forall(!Seq(
      "Delay",
      "Repeat",
      "Tape"
    ).contains(_))
}

object GracenoteAiring {
//  implicit val result: GetResult[GracenoteAiring] = RowParser[GracenoteAiring]
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
    (__ \ "qualifiers").read[Seq[String]].orElse(Reads.pure(Seq.empty[String])) and
    (__ \ "program").read[GracenoteProgram]
  )(GracenoteAiring.apply _)

  implicit val writes: OWrites[GracenoteAiring] = Json.writes[GracenoteAiring]
}
