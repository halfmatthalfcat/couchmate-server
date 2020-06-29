package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class Episode(
  episodeId: Option[Long],
  seriesId: Option[Long],
  season: Option[Long],
  episode: Option[Long],
) extends Product with Serializable

object Episode extends JsonConfig {
  implicit val format: Format[Episode] = Json.format[Episode]
}
