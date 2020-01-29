package com.couchmate.data.models

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class Series(
  seriesId: Option[Long],
  extId: Long,
  seriesName: String,
  totalSeasons: Option[Long],
  totalEpisodes: Option[Long],
) extends Product with Serializable

object Series extends JsonConfig {
  implicit val format: OFormat[Series] = Json.format[Series]
  implicit val getResult: GetResult[Series] = GenGetResult[Series]
}
