package com.couchmate.common.models.data

import java.time.LocalDateTime

import com.couchmate.common.util.slick.RowParser
import com.couchmate.common.db.PgProfile.plainAPI._
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class Show(
  showId: Option[Long],
  extId: Long,
  `type`: ShowType,
  episodeId: Option[Long],
  sportEventId: Option[Long],
  title: String,
  description: String,
  originalAirDate: Option[LocalDateTime]
)

object Show extends JsonConfig {
  implicit val format: Format[Show] = Json.format[Show]
  implicit val rowParser: GetResult[Show] = RowParser[Show]
}
