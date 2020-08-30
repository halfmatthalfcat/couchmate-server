package com.couchmate.common.models.data

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.util.slick.RowParser
import com.couchmate.common.db.PgProfile.plainAPI._

import slick.jdbc.GetResult

case class AiringStatus(
  airingId: Option[String],
  showId: Long,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  shortCode: Option[String],
  status: RoomStatusType
)

object AiringStatus {
  implicit val rowParser: GetResult[AiringStatus] = RowParser[AiringStatus]
}
