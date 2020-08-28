package com.couchmate.common.models.data

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.util.slick.RowParser
import com.couchmate.common.db.PgProfile.plainAPI._
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class Airing(
  airingId: Option[UUID],
  shortCode: Option[String],
  showId: Long,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
)

object Airing extends JsonConfig {
  implicit val format: Format[Airing] = Json.format[Airing]
  implicit val rowParser: GetResult[Airing] = RowParser[Airing]
}
