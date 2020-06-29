package com.couchmate.common.models.data

import java.time.LocalDateTime

import play.api.libs.json.{Format, Json}

case class Show(
  showId: Option[Long],
  extId: Long,
  `type`: ShowType,
  episodeId: Option[Long],
  sportEventId: Option[Long],
  title: String,
  description: String,
  originalAirDate: Option[LocalDateTime]
)

object Show extends JsonConfig {
  implicit val format: Format[Show] = Json.format[Show]
}
