package com.couchmate.external.gracenote.models

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class GracenoteProgram(
  tmsId: String,
  rootId: Long,
  title: String,
  shortDescription: Option[String],
  longDescription: Option[String],
  origAirDate: Option[LocalDateTime],
  releaseDate: Option[LocalDateTime],
  entityType: GracenoteProgramType,
  subType: GracenoteProgramSubtype,
  // -- Start Series
  seriesId: Option[Long],
  seasonNumber: Option[Long],
  episodeNumber: Option[Long],
  episodeTitle: Option[String],
  // -- End Series
  // -- Start Sport
  eventTitle: Option[String],
  organizationId: Option[Long],
  sportsId: Option[Long],
  gameDate: Option[LocalDateTime],
  // -- End Sport
) extends Product with Serializable {
  def isSport: Boolean = {
    sportsId.isDefined
  }

  def isSeries: Boolean = {
    seriesId.isDefined &&
    !seriesId.contains(rootId)
  }
}

object GracenoteProgram {
  private[this] def dateToLocalDateTime(date: Option[String]): Option[LocalDateTime] = {
    if (date.isDefined) {
      try {
        Some(
          LocalDate.parse(date.get, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
        )
      } catch {
        case _: Throwable => None
      }
    } else {
      None
    }
  }

  implicit val reads: Reads[GracenoteProgram] = (
    (__ \ "tmsId").read[String] and
    (
      (__ \ "rootId").read[String].map(_.toLong) or
      (__ \ "rootId").read[Long]
    ) and
    (__ \ "title").readWithDefault[String]("Unknown Program") and
    (__ \ "shortDescription").readNullable[String] and
    (__ \ "longDescription").readNullable[String] and
    (__ \ "origAirDate").readNullable[String].map(dateToLocalDateTime) and
    (__ \ "releaseDate").readNullable[String].map(dateToLocalDateTime) and
    (__ \ "entityType").readWithDefault[GracenoteProgramType](GracenoteProgramType.Show) and
    (__ \ "subType").readWithDefault[GracenoteProgramSubtype](GracenoteProgramSubtype.Unknown) and
    // -- Start Series
    (
      (__ \ "seriesId").readNullable[String].map(_.map(_.toLong)) or
      (__ \ "seriesId").readNullable[Long]
    ) and
    (
      (__ \ "seasonNumber").readNullable[String].map(_.map(_.toLong)) or
      (__ \ "seasonNumber").readNullable[Long]
    ) and
    (
      (__ \ "episodeNumber").readNullable[String].map(_.map(_.toLong)) or
      (__ \ "episodeNumber").readNullable[Long]
    ) and
    (__ \ "episodeTitle").readNullable[String] and
    // -- End Series
    // -- Start Sport
    (__ \ "eventTitle").readNullable[String] and
    (
      (__ \ "organizationId").readNullable[String].map(_.map(_.toLong)) or
      (__ \ "organizationid").readNullable[Long]
    ) and
    (
      (__ \ "sportsId").readNullable[String].map(_.map(_.toLong)) or
      (__ \ "sportsId").readNullable[Long]
    ) and
    (__ \ "gameDate").readNullable[String].map(dateToLocalDateTime)
    // -- End Sport
  )(GracenoteProgram.apply _)

  implicit val writes: OWrites[GracenoteProgram] = Json.writes[GracenoteProgram]
}
