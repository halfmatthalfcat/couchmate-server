package com.couchmate.common.models.thirdparty.gracenote

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
  seasonNum: Option[Long],
  episodeNum: Option[Long],
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
    sportsId.isDefined &&
    entityType == GracenoteProgramType.Sports
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
    (__ \ "title")
      .readWithDefault[String]("Local Programming")
      .map[String] {
        case "SIGN OFF" => "Local Programming"
        case _ @ title => title
    } and
    (__ \ "shortDescription").readNullable[String].map {
      case Some("Sign off.") => Some("Local Programming")
      case _ @ description => description
    } and
    (__ \ "longDescription").readNullable[String].map {
      case Some("Sign off.") => Some("Local Programming")
      case _ @ description => description
    } and
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
      (__ \ "seasonNum").readNullable[String].map(_.map(_.toLong)) or
      (__ \ "seasonNum").readNullable[Long]
    ) and
    (
      (__ \ "episodeNum").readNullable[String].map(_.map(_.toLong)) or
      (__ \ "episodeNum").readNullable[Long]
    ) and
    (__ \ "episodeTitle").readNullable[String].map {
      case Some("SIGN OFF") => Some("Local Programming")
      case _ @ title => title
    } and
    // -- End Series
    // -- Start Sport
    (__ \ "eventTitle").readNullable[String] and
    (
      (__ \ "organizationId").readNullable[String].map(_.map(_.toLong)) or
      (__ \ "organizationId").readNullable[Long]
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
