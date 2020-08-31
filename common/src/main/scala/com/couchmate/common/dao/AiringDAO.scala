package com.couchmate.common.dao

import java.time.LocalDateTime

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.api.grid.AiringConversion
import com.couchmate.common.models.data.{Airing, AiringStatus, RoomStatusType, Series, Show, ShowDetailed, SportEvent}
import com.couchmate.common.tables.{AiringTable, EpisodeTable, SeriesTable, ShowTable, SportEventTable}
import slick.dbio.Effect
import slick.sql.{SqlAction, SqlStreamingAction}

import scala.concurrent.{ExecutionContext, Future}

trait AiringDAO {

  def getAiring(airingId: String)(
    implicit
    db: Database
  ): Future[Option[Airing]] = {
    db.run(AiringDAO.getAiring(airingId))
  }

  def getAiring$()(
    implicit
    session: SlickSession
  ): Flow[String, Option[Airing], NotUsed] =
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

  def getAiringStatus(airingId: String)(
    implicit
    db: Database
  ): Future[Option[AiringStatus]] =
    db.run(AiringDAO.getAiringStatus(airingId).headOption)

  def getShowFromAiring(airingId: String)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Option[ShowDetailed]] =
    db.run(AiringDAO.getShowFromAiring(airingId)).map {
      case Some((s: Show, series: Option[Series], se: Option[SportEvent])) => Some(ShowDetailed(
        `type` = s.`type`,
        title = s.title,
        description = s.description,
        seriesTitle = series.map(_.seriesName),
        sportEventTitle = se.map(_.sportEventTitle),
        originalAirDate = s.originalAirDate,
      ))
      case _ => Option.empty
    }

  def getAiringFromGracenote(convert: AiringConversion)(
    implicit
    db: Database
  ): Future[Option[String]] =
    db.run(AiringDAO.getAiringFromGracenote(convert).headOption)

  def getAiringsFromGracenote(converts: Seq[AiringConversion])(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Seq[String]] =
    Future.sequence(
      converts.map(c =>
        db.run(AiringDAO.getAiringFromGracenote(c).headOption)
      )
    ).map(_.filter(_.isDefined).map(_.get))
}

object AiringDAO {
  private[this] lazy val getAiringQuery = Compiled { (airingId: Rep[String]) =>
    AiringTable.table.filter(_.airingId === airingId)
  }

  private[common] def getAiring(airingId: String): DBIO[Option[Airing]] =
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
      airingId = Some(Airing.generateShortcode)
    )
  ) { (airingId: String) => for {
    _ <- AiringTable
      .table
      .filter(_.airingId === airingId)
      .update(airing)
    updated <- AiringDAO.getAiring(airingId)
  } yield updated.get}.transactionally

  private[common] def getShowFromAiring(airingId: String)(
    implicit
    ec: ExecutionContext
  ): DBIO[Option[(Show, Option[Series], Option[SportEvent])]] = (for {
    a <- AiringTable.table if a.airingId === airingId
    ((s, _), series) <- (ShowTable.table
                  .joinLeft(EpisodeTable.table).on(_.episodeId === _.episodeId)
                  .joinLeft(SeriesTable.table).on(_._2.map(_.seriesId).flatten === _.seriesId)) if s.showId === a.showId
    (_, se) <- ShowTable.table.joinLeft(SportEventTable.table).on(_.sportEventId === _.sportEventId)
  } yield (s, series, se)).result.headOption

  private[common] def addOrGetAiring(a: Airing) =
    sql"""
         WITH input_rows(airing_id, show_id, start_time, end_time, duration) AS (
          VALUES (${a.airingId}, ${a.showId}, ${a.startTime}::timestamp, ${a.endTime}::timestamp, ${a.duration})
         ), ins AS (
          INSERT INTO airing AS a (airing_id, show_id, start_time, end_time, duration)
          SELECT * FROM input_rows
          ON CONFLICT (show_id, start_time, end_time) DO NOTHING
          RETURNING airing_id, show_id, start_time, end_time, duration
         ), sel AS (
          SELECT airing_id, show_id, start_time, end_time, duration
          FROM ins
          UNION ALL
          SELECT a.airing_id, a.show_id, start_time, end_time, a.duration
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
                  end_time = air.end_time
           RETURNING airing_id, show_id, start_time, end_time, duration
         )  SELECT airing_id, show_id, start_time, end_time, duration FROM sel
            UNION  ALL
            TABLE  ups;
         """.as[Airing]

  private[common] def getAiringStatus(airingId: String): SqlStreamingAction[Seq[AiringStatus], AiringStatus, Effect] =
    sql"""
       SELECT  a.airing_id, a.show_id, a.start_time, a.end_time, a.duration, CASE
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

  private[common] def getAiringFromGracenote(convert: AiringConversion) =
    sql"""
         SELECT a.airing_id
         FROM   airing as a
         JOIN   lineup as l
         ON     l.airing_id = a.airing_id
         JOIN   show as s
         ON     s.show_id = a.show_id
         JOIN   provider_channel pc
         ON     l.provider_channel_id = pc.provider_channel_id
         JOIN   provider as p
         ON     p.provider_id = pc.provider_id
         JOIN   channel as c
         ON     c.channel_id = pc.channel_id
         WHERE  p.ext_id = ${convert.provider} AND
                c.ext_id = ${convert.extChannelId} AND
                s.ext_id = ${convert.extShowId} AND
                a.start_time = ${convert.startTime}
         """.as[String]
}
