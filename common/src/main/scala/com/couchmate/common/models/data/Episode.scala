package com.couchmate.common.models.data

import com.couchmate.common.util.slick.RowParser
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class Episode(
  episodeId: Option[Long],
  seriesId: Option[Long],
  season: Long,
  episode: Long,
) extends Product with Serializable

object Episode extends JsonConfig {
  implicit val format: Format[Episode] = Json.format[Episode]
  implicit val rowParser: GetResult[Episode] = RowParser[Episode]
}
