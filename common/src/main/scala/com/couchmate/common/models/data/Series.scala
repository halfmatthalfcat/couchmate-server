package com.couchmate.common.models.data

import com.couchmate.common.util.slick.RowParser
import com.couchmate.common.db.PgProfile.plainAPI._
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class Series(
  seriesId: Option[Long],
  extId: Long,
  seriesName: String,
  totalSeasons: Option[Long],
  totalEpisodes: Option[Long],
) extends Product with Serializable

object Series extends JsonConfig {
  implicit val format: Format[Series] = Json.format[Series]
  implicit val rowParser: GetResult[Series] = RowParser[Series]
}
