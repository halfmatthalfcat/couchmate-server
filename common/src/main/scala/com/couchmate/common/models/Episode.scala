package com.couchmate.common.models

import play.api.libs.json.{Json, OFormat}

case class Episode(
  episodeId: Option[Long],
  seriesId: Long,
  season: Option[Int],
  episode: Option[Int],
) extends Product with Serializable

object Episode extends JsonConfig {
  implicit val format: OFormat[Episode] = Json.format[Episode]
}
