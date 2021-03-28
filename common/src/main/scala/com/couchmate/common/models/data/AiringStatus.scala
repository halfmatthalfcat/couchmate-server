package com.couchmate.common.models.data

import java.time.LocalDateTime
import java.util.UUID
import com.couchmate.common.util.slick.RowParser
import com.couchmate.common.db.PgProfile.plainAPI._
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class AiringStatus(
  airingId: Option[String],
  showId: Long,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  isNew: Boolean,
  status: RoomStatusType
)

object AiringStatus {
  implicit val format: Format[AiringStatus] = Json.format[AiringStatus]
  implicit val rowParser: GetResult[AiringStatus] = RowParser[AiringStatus]
}
