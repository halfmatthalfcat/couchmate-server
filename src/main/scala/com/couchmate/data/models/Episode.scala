package com.couchmate.data.models

import play.api.libs.json.{Json, OFormat}

case class Episode(
  episodeId: Option[Long],
  seriesId: Long,
  season: Option[Long],
  episode: Option[Long],
) extends Product with Serializable

object Episode extends JsonConfig {
  implicit val format: OFormat[Episode] = Json.format[Episode]
}
