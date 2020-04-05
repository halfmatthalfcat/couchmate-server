package com.couchmate.data.db.table

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.Airing
import slick.lifted.Tag
import slick.migration.api._

class AiringTable(tag: Tag) extends Table[Airing](tag, "airing") {
  def airingId: Rep[UUID] = column[UUID]("airing_id", O.PrimaryKey, O.SqlType("uuid"))
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

object AiringTable extends Slickable[AiringTable] {
  private[db] val table = TableQuery[AiringTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.airingId,
      _.showId,
      _.startTime,
      _.endTime,
      _.duration,
    ).addForeignKeys(
      _.showFk,
    ).addIndexes(
      _.showStartTimeIdx,
      _.startTimeIdx,
      _.endTimeIdx,
    )
}
