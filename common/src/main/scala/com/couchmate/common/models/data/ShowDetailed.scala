package com.couchmate.common.models.data

import java.time.LocalDateTime

import play.api.libs.json.{Format, Json}

case class ShowDetailed(
  `type`: ShowType,
  title: String,
  description: String,
  seriesTitle: Option[String],
  sportEventTitle: Option[String],
  originalAirDate: Option[LocalDateTime]
)

object ShowDetailed {
  implicit val format: Format[ShowDetailed] = Json.format[ShowDetailed]
}