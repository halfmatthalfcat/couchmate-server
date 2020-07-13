package com.couchmate.common.dao

import java.time.LocalDateTime
import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{Airing, AiringStatus, RoomStatusType}
import com.couchmate.common.tables.AiringTable
import slick.sql.SqlStreamingAction

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

  def getAiringStatus(airingId: UUID)(
    implicit
    db: Database
  ): Future[Option[AiringStatus]] =
    db.run(AiringDAO.getAiringStatus(airingId).headOption)
}

object AiringDAO {
  private[this] lazy val getAiringQuery = Compiled { (airingId: Rep[UUID]) =>
    AiringTable.table.filter(_.airingId === airingId)
  }

  private[common] def getAiring(airingId: UUID): DBIO[Option[Airing]] =
    getAiringQuery(airingId).result.headOption

  private[this] lazy val getAiringByShowStartAndEndQuery = Compiled {
    (showId: Rep[Long], startTime: Rep[LocalDateTime], endTime: Rep[LocalDateTime]) =>
      AiringTable.table.filter { airing =>
        airing.showId === showId &&
        airing.startTime === startTime &&
        airing.endTime === endTime
      }
  }

  private[common] def getAiringByShowStartAndEnd(
    showId: Long,
    startTime: LocalDateTime,
    endTime: LocalDateTime
  ): DBIO[Option[Airing]] =
    getAiringByShowStartAndEndQuery(showId, startTime, endTime).result.headOption

  private[this] lazy val getAiringsByStartQuery = Compiled { (startTime: Rep[LocalDateTime]) =>
    AiringTable.table.filter(_.startTime === startTime)
  }

  private[common] def getAiringsByStart(startTime: LocalDateTime): DBIO[Seq[Airing]] =
    getAiringsByStartQuery(startTime).result

  private[this] lazy val getAiringsByEndQuery = Compiled { (endTime: Rep[LocalDateTime]) =>
    AiringTable.table.filter(_.endTime === endTime)
  }

  private[common] def getAiringsByEnd(endTime: LocalDateTime): DBIO[Seq[Airing]] =
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

  private[common] def getAiringsBetweenStartAndEnd(
    startTime: LocalDateTime,
    endTime: LocalDateTime
  ): DBIO[Seq[Airing]] =
    getAiringsBetweenStartAndEndQuery(startTime, endTime).result

  private[common] def upsertAiring(airing: Airing)(
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

  private[common] def addOrGetAiring(a: Airing) =
    sql"""
         WITH input_rows(airing_id, show_id, start_time, end_time, duration) AS (
          VALUES (${a.airingId}::uuid, ${a.showId}, ${a.startTime}::timestamp, ${a.endTime}::timestamp, ${a.duration})
         ), ins AS (
          INSERT INTO airing AS a (airing_id, show_id, start_time, end_time, duration)
          SELECT * FROM input_rows
          ON CONFLICT (show_id, start_time, end_time) DO NOTHING
          RETURNING airing_id, show_id, start_time, end_time, duration
         ), sel AS (
          SELECT airing_id, show_id, start_time, end_time, duration
          FROM ins
          UNION ALL
          SELECT a.airing_id, show_id, start_time, end_time, a.duration
          FROM input_rows
          JOIN airing as a USING (show_id, start_time, end_time)
         ), ups AS (
           INSERT INTO airing AS air (airing_id, show_id, start_time, end_time, duration)
           SELECT i.*
           FROM   input_rows i
           LEFT   JOIN sel   s USING (show_id, start_time, end_time)
           WHERE  s.show_id IS NULL
           ON     CONFLICT (show_id, start_time, end_time) DO UPDATE
           SET    show_id = air.show_id,
                  start_time = air.start_time,
                  end_time = air.end_time,
                  duration = air.duration
           RETURNING airing_id, show_id, start_time, end_time, duration
         )  SELECT airing_id, show_id, start_time, end_time, duration FROM sel
            UNION  ALL
            TABLE  ups;
         """.as[Airing]

  private[common] def getAiringStatus(airingId: UUID): SqlStreamingAction[Seq[AiringStatus], AiringStatus, Effect] =
    sql"""
       SELECT  a.*, CASE
        WHEN    EXTRACT(EPOCH FROM(start_time) - TIMEZONE('utc', NOW())) / 60 <= 15 AND
                EXTRACT(EPOCH FROM(start_time) - TIMEZONE('utc', NOW())) / 60 > 0
                THEN ${RoomStatusType.PreGame}
        WHEN    EXTRACT(EPOCH FROM(start_time) - TIMEZONE('utc', NOW())) / 60 <= 0 AND
                duration - (EXTRACT(EPOCH FROM(end_time) - TIMEZONE('utc', NOW())) / 60) <= duration
                THEN ${RoomStatusType.Open}
        WHEN    EXTRACT(EPOCH FROM(end_time) - TIMEZONE('utc', NOW())) / 60 < 0 AND
                EXTRACT(EPOCH FROM(end_time) - TIMEZONE('utc', NOW())) / 60 >= -15
                THEN ${RoomStatusType.PostGame}
        ELSE    ${RoomStatusType.Closed}
        END AS  status
        FROM    airing as a
        WHERE   airing_id = $airingId
      """.as[AiringStatus]
}
