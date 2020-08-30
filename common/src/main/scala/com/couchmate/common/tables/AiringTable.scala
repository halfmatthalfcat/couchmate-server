package com.couchmate.common.tables

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.Airing
import com.couchmate.common.util.slick.WithTableQuery

class AiringTable(tag: Tag) extends Table[Airing](tag, "airing") {
  def airingId: Rep[String] = column[String]("airing_id", O.PrimaryKey)
  def showId: Rep[Long] = column[Long]("show_id")
  def startTime: Rep[LocalDateTime] = column[LocalDateTime]("start_time", O.SqlType("timestamp"))
  def endTime: Rep[LocalDateTime] = column[LocalDateTime]("end_time", O.SqlType("timestamp"))
  def duration: Rep[Int] = column[Int]("duration")
  def * = (
    airingId.?,
    showId,
    startTime,
    endTime,
    duration,
  ) <> ((Airing.apply _).tupled, Airing.unapply)

  def showFk = foreignKey(
    "airing_show_fk",
    showId,
    ShowTable.table,
    )(
    _.showId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def showStartTimeIdx = index(
    "show_timing_idx",
    (showId, startTime, endTime),
    unique = true
  )

  def startTimeIdx = index(
    "airing_start_time_idx",
    startTime,
  )

  def endTimeIdx = index(
    "airing_end_time_idx",
    endTime
  )
}

object AiringTable extends WithTableQuery[AiringTable] {
  private[couchmate] val table = TableQuery[AiringTable]
}
