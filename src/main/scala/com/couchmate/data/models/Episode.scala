package com.couchmate.data.models

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class Episode(
  episodeId: Option[Long],
  seriesId: Long,
  season: Option[Int],
  episode: Option[Int],
) extends Product with Serializable

object Episode extends JsonConfig {
  implicit val format: OFormat[Episode] = Json.format[Episode]
  implicit val getResult: GetResult[Episode] = GenGetResult[Episode]
}
