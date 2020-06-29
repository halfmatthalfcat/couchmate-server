package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class Series(
  seriesId: Option[Long],
  extId: Long,
  seriesName: String,
  totalSeasons: Option[Long],
  totalEpisodes: Option[Long],
) extends Product with Serializable

object Series extends JsonConfig {
  implicit val format: Format[Series] = Json.format[Series]
}
