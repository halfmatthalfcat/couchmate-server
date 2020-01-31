package com.couchmate.db

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.models.Airing
import com.couchmate.db.PgProfile.api._
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
    "show_start_time_idx",
    (showId, startTime),
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
  val table = TableQuery[AiringTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
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

//  private[this] lazy val getAiringCompiled = Compiled { (airingId: Rep[UUID]) =>
//    airingTable.filter(_.airingId === airingId)
//  }
//
//  def getAiring(airingId: UUID): AppliedCompiledFunction[UUID, Query[AiringTable, AiringTable, Seq], Seq[AiringTable]] = {
//    getAiringCompiled(airingId)
//  }
//
//
//  private[this] lazy val getAiringByShowAndStartCompiled = Compiled { (showId: Rep[Long], startTime: Rep[LocalDateTime]) =>
//    airingTable.filter { airing =>
//      airing.showId === showId &&
//      airing.startTime === startTime
//    }
//  }
//
//  def getAiringByShowAndStart(showId: Long, startTime: LocalDateTime): AppliedCompiledFunction[(Long, LocalDateTime), Query[AiringTable, AiringTable, Seq], Seq[AiringTable]] = {
//    getAiringByShowAndStartCompiled(showId, startTime)
//  }
//
//  private[this] lazy val getAiringsByStartCompiled = Compiled { (startTime: Rep[LocalDateTime]) =>
//    airingTable.filter(_.startTime === startTime)
//  }
//
//  def getAiringsByStart(startTime: LocalDateTime): AppliedCompiledFunction[LocalDateTime, Query[AiringTable, AiringTable, Seq], Seq[AiringTable]] = {
//    getAiringsByStartCompiled(startTime)
//  }
//
//  private[this] lazy val getAiringsByEndCompiled = Compiled { (endTime: Rep[LocalDateTime]) =>
//    airingTable.filter(_.endTime === endTime)
//  }
//
//  def getAiringsByEnd(endTime: LocalDateTime): AppliedCompiledFunction[LocalDateTime, Query[AiringTable, AiringTable, Seq], Seq[AiringTable]] = {
//    getAiringsByEndCompiled(endTime)
//  }
//
//  private[this] lazy val getAiringsForStartAndDurationCompiled = Compiled { (startTime: Rep[LocalDateTime], endTime: Rep[LocalDateTime]) =>
//    airingTable.filter { airing =>
//      (airing.startTime between (startTime, endTime)) ||
//      (airing.endTime between (startTime, endTime)) ||
//      (
//        airing.startTime <= startTime &&
//        airing.endTime >= endTime
//      )
//    }
//  }
//
//  def getAiringsForStartAndDuration(startTime: LocalDateTime, duration: Int): AppliedCompiledFunction[(LocalDateTime, LocalDateTime), Query[AiringTable, AiringTable, Seq], Seq[AiringTable]] = {
//    val endTime: LocalDateTime = startTime.plusMinutes(duration);
//    getAiringsForStartAndDurationCompiled(startTime, endTime)
//  }
//
//  def upsertAiring(a: AiringTable): SqlStreamingAction[Vector[AiringTable], AiringTable, Effect] = {
//    sql"""
//         INSERT INTO airing
//         (airing_id, show_id, start_time, end_time, duration)
//         VALUES
//         (${a.airingId}, ${a.showId}, ${a.startTime}, ${a.endTime}, ${a.duration})
//         ON CONFLICT (airing_id)
//         DO UPDATE SET
//            show_id =     ${a.showId},
//            start_time =  ${a.startTime},
//            end_time =    ${a.endTime},
//            duration =    ${a.duration}
//         RETURNING *
//       """.as[AiringTable]
//  }
}
