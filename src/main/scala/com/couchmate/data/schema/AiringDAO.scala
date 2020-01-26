package com.couchmate.data.schema

import java.time.OffsetDateTime
import java.util.UUID

import PgProfile.api._
import slick.migration.api._
import com.couchmate.data.models.Airing
import slick.lifted.Tag

import scala.concurrent.{ExecutionContext, Future}

class AiringDAO(tag: Tag) extends Table[Airing](tag, "airing") {
  def airingId: Rep[UUID] = column[UUID]("airing_id", O.PrimaryKey, O.SqlType("uuid"))
  def showId: Rep[Long] = column[Long]("show_id")
  def startTime: Rep[OffsetDateTime] = column[OffsetDateTime]("start_time", O.SqlType("timestamptz"))
  def endTime: Rep[OffsetDateTime] = column[OffsetDateTime]("end_time", O.SqlType("timestamptz"))
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

  def getAiring(airingId: UUID)(
    implicit
    db: Database,
  ): Future[Option[Airing]] = {
    db.run(airingTable.filter(_.airingId === airingId).result.headOption)
  }

  def getAiringByShowAndStart(showId: Long, startTime: OffsetDateTime)(
    implicit
    db: Database,
  ): Future[Option[Airing]] = {
    db.run(airingTable.filter { airing =>
      airing.showId === showId &&
      airing.startTime === startTime
    }.result.headOption)
  }

  def getAiringsByStart(startTime: OffsetDateTime)(
    implicit
    db: Database,
  ): Future[Seq[Airing]] = {
    db.run(airingTable.filter(_.startTime === startTime).result)
  }

  def getAiringsByEnd(endTime: OffsetDateTime)(
    implicit
    db: Database,
  ): Future[Seq[Airing]] = {
    db.run(airingTable.filter(_.endTime === endTime).result)
  }

  def getAiringsForStartAndDuration(startTime: OffsetDateTime, duration: Int)(
    implicit
    db: Database,
  ): Future[Seq[Airing]] = {
    val endTime: OffsetDateTime = startTime.plusMinutes(duration);
    db.run(airingTable.filter { airing =>
      (airing.startTime between (startTime, endTime)) ||
      (airing.endTime between (startTime, endTime)) ||
      (
        airing.startTime <= startTime &&
        airing.endTime >= endTime
      )
    }.result)
  }

  def upsertAiring(airing: Airing)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Airing] = {
    airing match {
      case Airing(None, _, _, _, _) =>
        db.run((airingTable returning airingTable) += airing)
      case Airing(Some(airingId), _, _, _, _) => for {
        _ <- db.run(airingTable.filter(_.airingId === airingId).update(airing))
        a <- db.run(airingTable.filter(_.airingId === airingId).result.head)
      } yield a
    }
  }
}
