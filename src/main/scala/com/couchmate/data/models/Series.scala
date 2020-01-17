package com.couchmate.data.models

import play.api.libs.json.{Json, OFormat}

case class Series(
  seriesId: Option[Long],
  sourceId: Long,
  extId: Long,
  seriesName: String,
  totalSeasons: Option[Long],
  totalEpisodes: Option[Long],
) extends Product with Serializable

object Series extends JsonConfig {
  implicit val format: OFormat[Series] = Json.format[Series]
}
