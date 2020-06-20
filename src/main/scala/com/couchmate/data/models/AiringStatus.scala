package com.couchmate.data.models

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.util.slick.RowParser
import com.couchmate.data.db.PgProfile.plainAPI._
import slick.jdbc.GetResult

case class AiringStatus(
  airingId: Option[UUID],
  showId: Long,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  status: RoomStatusType
)

object AiringStatus {
  implicit val rowParser: GetResult[AiringStatus] = RowParser[AiringStatus]
}
