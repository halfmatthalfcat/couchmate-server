package com.couchmate.common.models.api.grid

import com.couchmate.common.util.slick.RowParser
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class GridSeries(
  seriesId: Long,
  seriesTitle: String,
  episodeId: Long,
  season: Long,
  episode: Long,
  following: Long,
)

object GridSeries {
  implicit val format: Format[GridSeries] = Json.format[GridSeries]
  implicit val rowParser: GetResult[GridSeries] = RowParser[GridSeries]
}