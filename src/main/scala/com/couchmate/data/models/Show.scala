package com.couchmate.data.models

import java.time.LocalDateTime

import play.api.libs.json.{Json, OFormat}

case class Show(
  showId: Option[Long],
  extId: Long,
  `type`: String,
  episodeId: Option[Long],
  sportEventId: Option[Long],
  title: String,
  description: String,
  originalAirDate: Option[LocalDateTime]
) extends Product with Serializable

object Show extends JsonConfig {
  implicit val format: OFormat[Show] = Json.format[Show]
}
