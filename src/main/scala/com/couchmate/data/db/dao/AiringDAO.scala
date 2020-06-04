package com.couchmate.data.db.dao

import java.time.LocalDateTime
import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.AiringTable
import com.couchmate.data.models.Airing
import slick.lifted.{Compiled, Rep}

import scala.concurrent.{ExecutionContext, Future}

trait AiringDAO {

  def getAiring(airingId: UUID)(
    implicit
    db: Database
  ): Future[Option[Airing]] = {
    db.run(AiringDAO.getAiring(airingId))
  }

  def getAiring$()(
    implicit
    session: SlickSession
  ): Flow[UUID, Option[Airing], NotUsed] =
    Slick.flowWithPassThrough(AiringDAO.getAiring)

  def getAiringsByStart(startTime: LocalDateTime)(
    implicit
    db: Database
  ): Future[Seq[Airing]] = {
    db.run(AiringDAO.getAiringsByStart(startTime))
  }

  def getAiringsByStart$()(
    implicit
    session: SlickSession
  ): Flow[LocalDateTime, Seq[Airing], NotUsed] =
    Slick.flowWithPassThrough(AiringDAO.getAiringsByStart)

  def getAiringsByEnd(endTime: LocalDateTime)(
    implicit
    db: Database
  ): Future[Seq[Airing]] = {
    db.run(AiringDAO.getAiringsByEnd(endTime))
  }

  def getAiringsByEnd$()(
    implicit
    session: SlickSession
  ): Flow[LocalDateTime, Seq[Airing], NotUsed] =
    Slick.flowWithPassThrough(AiringDAO.getAiringsByEnd)

  def getAiringByShowStartAndEnd(showId: Long, startTime: LocalDateTime, endTime: LocalDateTime)(
    implicit
    db: Database
  ): Future[Option[Airing]] =
    db.run(AiringDAO.getAiringByShowStartAndEnd(showId, startTime, endTime))

  def getAiringByShowStartAndEnd$()(
    implicit
    session: SlickSession
  ): Flow[(Long, LocalDateTime, LocalDateTime), Option[Airing], NotUsed] =
    Slick.flowWithPassThrough(
      (AiringDAO.getAiringByShowStartAndEnd _).tupled
    )

  def getAiringsByStartAndDuration(startTime: LocalDateTime, duration: Int)(
    implicit
    db: Database
  ): Future[Seq[Airing]] = {
    val endTime: LocalDateTime = startTime.plusMinutes(duration)
    db.run(AiringDAO.getAiringsBetweenStartAndEnd(startTime, endTime))
  }

  def getAiringsByStartAndDuration$()(
    implicit
    session: SlickSession
  ): Flow[(LocalDateTime, Int), Seq[Airing], NotUsed] =
    Flow[(LocalDateTime, Int)].map(tuple => (
        tuple._1, tuple._1.plusMinutes(tuple._2)
      )).via(
        Slick.flowWithPassThrough((AiringDAO.getAiringsBetweenStartAndEnd _).tupled)
      )

  def upsertAiring(airing: Airing)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Airing] =
    db.run(AiringDAO.upsertAiring(airing))

  def upsertAiring$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[Airing, Airing, NotUsed] =
    Slick.flowWithPassThrough(AiringDAO.upsertAiring)
}

object AiringDAO {
  private[this] lazy val getAiringQuery = Compiled { (airingId: Rep[UUID]) =>
    AiringTable.table.filter(_.airingId === airingId)
  }

  private[dao] def getAiring(airingId: UUID): DBIO[Option[Airing]] =
    getAiringQuery(airingId).result.headOption

  private[this] lazy val getAiringByShowStartAndEndQuery = Compiled {
    (showId: Rep[Long], startTime: Rep[LocalDateTime], endTime: Rep[LocalDateTime]) =>
      AiringTable.table.filter { airing =>
        airing.showId === showId &&
        airing.startTime === startTime &&
        airing.endTime === endTime
      }
  }

  private[dao] def getAiringByShowStartAndEnd(
    showId: Long,
    startTime: LocalDateTime,
    endTime: LocalDateTime
  ): DBIO[Option[Airing]] =
    getAiringByShowStartAndEndQuery(showId, startTime, endTime).result.headOption

  private[this] lazy val getAiringsByStartQuery = Compiled { (startTime: Rep[LocalDateTime]) =>
    AiringTable.table.filter(_.startTime === startTime)
  }

  private[dao] def getAiringsByStart(startTime: LocalDateTime): DBIO[Seq[Airing]] =
    getAiringsByStartQuery(startTime).result

  private[this] lazy val getAiringsByEndQuery = Compiled { (endTime: Rep[LocalDateTime]) =>
    AiringTable.table.filter(_.endTime === endTime)
  }

  private[dao] def getAiringsByEnd(endTime: LocalDateTime): DBIO[Seq[Airing]] =
    getAiringsByEndQuery(endTime).result

  private[this] lazy val getAiringsBetweenStartAndEndQuery = Compiled {
    (startTime: Rep[LocalDateTime], endTime: Rep[LocalDateTime]) =>
      AiringTable.table.filter { airing =>
        (airing.startTime between (startTime, endTime)) &&
        (airing.endTime between (startTime, endTime)) &&
        (
          airing.startTime <= startTime &&
          airing.endTime >= endTime
        )
      }
  }

  private[dao] def getAiringsBetweenStartAndEnd(
    startTime: LocalDateTime,
    endTime: LocalDateTime
  ): DBIO[Seq[Airing]] =
    getAiringsBetweenStartAndEndQuery(startTime, endTime).result

  private[dao] def upsertAiring(airing: Airing)(
    implicit
    ec: ExecutionContext
  ): DBIO[Airing] = airing.airingId.fold[DBIO[Airing]](
    (AiringTable.table returning AiringTable.table) += airing.copy(
      airingId = Some(UUID.randomUUID())
    )
  ) { (airingId: UUID) => for {
    _ <- AiringTable
      .table
      .filter(_.airingId === airingId)
      .update(airing)
    updated <- AiringDAO.getAiring(airingId)
  } yield updated.get}.transactionally
}
