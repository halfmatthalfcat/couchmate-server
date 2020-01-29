package com.couchmate.data.schema

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.data.models.Airing
import com.couchmate.data.schema.PgProfile.api._
import slick.dbio.Effect
import slick.lifted.{AppliedCompiledFunction, Tag}
import slick.migration.api._
import slick.sql.SqlStreamingAction

class AiringDAO(tag: Tag) extends Table[Airing](tag, "airing") {
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
    ShowDAO.showTable,
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

object AiringDAO {
  val airingTable = TableQuery[AiringDAO]

  val init = TableMigration(airingTable)
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

  private[this] lazy val getAiringCompiled = Compiled { (airingId: Rep[UUID]) =>
    airingTable.filter(_.airingId === airingId)
  }

  def getAiring(airingId: UUID): AppliedCompiledFunction[UUID, Query[AiringDAO, Airing, Seq], Seq[Airing]] = {
    getAiringCompiled(airingId)
  }


  private[this] lazy val getAiringByShowAndStartCompiled = Compiled { (showId: Rep[Long], startTime: Rep[LocalDateTime]) =>
    airingTable.filter { airing =>
      airing.showId === showId &&
      airing.startTime === startTime
    }
  }

  def getAiringByShowAndStart(showId: Long, startTime: LocalDateTime): AppliedCompiledFunction[(Long, LocalDateTime), Query[AiringDAO, Airing, Seq], Seq[Airing]] = {
    getAiringByShowAndStartCompiled(showId, startTime)
  }

  private[this] lazy val getAiringsByStartCompiled = Compiled { (startTime: Rep[LocalDateTime]) =>
    airingTable.filter(_.startTime === startTime)
  }

  def getAiringsByStart(startTime: LocalDateTime): AppliedCompiledFunction[LocalDateTime, Query[AiringDAO, Airing, Seq], Seq[Airing]] = {
    getAiringsByStartCompiled(startTime)
  }

  private[this] lazy val getAiringsByEndCompiled = Compiled { (endTime: Rep[LocalDateTime]) =>
    airingTable.filter(_.endTime === endTime)
  }

  def getAiringsByEnd(endTime: LocalDateTime): AppliedCompiledFunction[LocalDateTime, Query[AiringDAO, Airing, Seq], Seq[Airing]] = {
    getAiringsByEndCompiled(endTime)
  }

  private[this] lazy val getAiringsForStartAndDurationCompiled = Compiled { (startTime: Rep[LocalDateTime], endTime: Rep[LocalDateTime]) =>
    airingTable.filter { airing =>
      (airing.startTime between (startTime, endTime)) ||
      (airing.endTime between (startTime, endTime)) ||
      (
        airing.startTime <= startTime &&
        airing.endTime >= endTime
      )
    }
  }

  def getAiringsForStartAndDuration(startTime: LocalDateTime, duration: Int): AppliedCompiledFunction[(LocalDateTime, LocalDateTime), Query[AiringDAO, Airing, Seq], Seq[Airing]] = {
    val endTime: LocalDateTime = startTime.plusMinutes(duration);
    getAiringsForStartAndDurationCompiled(startTime, endTime)
  }

  def upsertAiring(a: Airing): SqlStreamingAction[Vector[Airing], Airing, Effect] = {
    sql"""
         INSERT INTO airing
         (airing_id, show_id, start_time, end_time, duration)
         VALUES
         (${a.airingId}, ${a.showId}, ${a.startTime}, ${a.endTime}, ${a.duration})
         ON CONFLICT (airing_id)
         DO UPDATE SET
            show_id =     ${a.showId},
            start_time =  ${a.startTime},
            end_time =    ${a.endTime},
            duration =    ${a.duration}
         RETURNING *
       """.as[Airing]
  }
}
