package com.couchmate.data.schema

import java.time.OffsetDateTime
import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.Airing
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

import scala.concurrent.ExecutionContext

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

  def getAiring()(
    implicit
    session: SlickSession,
  ): Flow[UUID, Option[Airing], NotUsed] = Slick.flowWithPassThrough { airingId =>
    airingTable.filter(_.airingId === airingId).result.headOption
  }

  def getAiringByShowAndStart()(
    implicit
    session: SlickSession,
  ): Flow[(Long, OffsetDateTime), Option[Airing], NotUsed] = Slick.flowWithPassThrough { case (airingId, startDate) =>
    airingTable.filter { airing =>
      airing.showId === airingId &&
      airing.startTime === startDate
    }.result.headOption
  }

  def getAiringsByStart()(
    implicit
    session: SlickSession,
  ): Flow[OffsetDateTime, Seq[Airing], NotUsed] = Slick.flowWithPassThrough { startTime =>
    airingTable.filter(_.startTime === startTime).result
  }

  def getAiringsByEnd()(
    implicit
    session: SlickSession,
  ): Flow[OffsetDateTime, Seq[Airing], NotUsed] = Slick.flowWithPassThrough { endTime =>
    airingTable.filter(_.endTime === endTime).result
  }

  def getAiringsForStartAndDuration()(
    implicit
    session: SlickSession,
  ): Flow[(OffsetDateTime, Int), Seq[Airing], NotUsed] = Slick.flowWithPassThrough {
    case (startTime, duration) =>
      val endTime: OffsetDateTime = startTime.plusMinutes(duration)
      airingTable.filter { airing =>
        (airing.startTime between (startTime, endTime)) ||
        (airing.endTime between (startTime, endTime)) ||
        (
          airing.startTime <= startTime &&
          airing.endTime >= endTime
        )
    }.result
  }

  def upsertAiring()(
    implicit
    db: SlickSession,
    ec: ExecutionContext,
  ): Flow[Airing, Airing, NotUsed] = Slick.flowWithPassThrough {
    case airing @ Airing(None, _, _, _, _) =>
      (airingTable returning airingTable) += airing.copy(airingId = Some(UUID.randomUUID()))
    case airing @ Airing(Some(airingId), _, _, _, _) => for {
      _ <- airingTable.filter(_.airingId === airingId).update(airing)
      a <- airingTable.filter(_.airingId === airingId).result.head
    } yield a
  }
}
