package com.couchmate.common.models.data

import com.couchmate.common.util.slick.RowParser
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class Lineup(
  lineupId: Option[Long],
  providerChannelId: Long,
  airingId: String,
  active: Boolean
) extends Product with Serializable

object Lineup extends JsonConfig {
  implicit val format: Format[Lineup] = Json.format[Lineup]
  implicit val rowParser: GetResult[Lineup] = RowParser[Lineup]

}
