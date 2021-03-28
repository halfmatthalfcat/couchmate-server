package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.api.grid.AiringConversion
import com.couchmate.common.models.data._
import com.couchmate.common.tables._
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object AiringDAO {
  private[this] lazy val getAiringQuery = Compiled { (airingId: Rep[String]) =>
    AiringTable.table.filter(_.airingId === airingId)
  }

  def getAiring(airingId: String)(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Airing]] = cache(
    "getAiring", airingId
  )(db.run(getAiringQuery(airingId).result.headOption))(
    bust = bust
  )

  private[this] lazy val getAiringsByStartQuery = Compiled { (startTime: Rep[LocalDateTime]) =>
    AiringTable.table.filter(_.startTime === startTime)
  }

  def getAiringsByStart(startTime: LocalDateTime)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[Airing]] = cache(
    "getAiringsByStart", startTime.toString
  )(db.run(getAiringsByStartQuery(startTime).result))()

  private[this] lazy val getAiringsByEndQuery = Compiled { (endTime: Rep[LocalDateTime]) =>
    AiringTable.table.filter(_.endTime === endTime)
  }

  def getAiringsByEnd(endTime: LocalDateTime)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[Airing]] = cache(
    "getAiringsByEnd", endTime.toString
  )(db.run(getAiringsByEndQuery(endTime).result))()

  private[this] lazy val getAiringByShowStartAndEndQuery = Compiled {
    (showId: Rep[Long], startTime: Rep[LocalDateTime], endTime: Rep[LocalDateTime]) =>
      AiringTable.table.filter { airing =>
        airing.showId === showId &&
        airing.startTime === startTime &&
        airing.endTime === endTime
      }
  }

  def getAiringByShowStartAndEnd(showId: Long, startTime: LocalDateTime, endTime: LocalDateTime)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Airing]] = cache(
    "getAiringByShowStartAndEnd", showId, startTime.toString, endTime.toString
  )(db.run(getAiringByShowStartAndEndQuery(
    showId,
    startTime,
    endTime
  ).result.headOption))()

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

  def getAiringsByStartAndDuration(startTime: LocalDateTime, duration: Int)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[Airing]] = cache(
    "getAiringsByStartAndDuration", startTime.toString, duration
  )({
    val endTime: LocalDateTime = startTime.plusMinutes(duration)
    db.run(getAiringsBetweenStartAndEndQuery(
      startTime,
      endTime
    ).result)
  })()

  private[this] lazy val getExtShowIdsByProviderChannelAndStartTimeQuery = Compiled {
    (providerChannelId: Rep[Long], startTime: Rep[LocalDateTime]) => for {
      l <- LineupTable.table if l.providerChannelId === providerChannelId
      a <- AiringTable.table if (
        a.airingId === l.airingId &&
        a.startTime === startTime
      )
      s <- ShowTable.table if s.showId === a.showId
    } yield s.extId
  }

  def getExtShowIdsByProviderChannelAndStartTime(
    providerChannelId: Long,
    startTime: LocalDateTime
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[Long]] = cache(
    "getExtShowIdsByProviderChannelAndStartTime",
    providerChannelId,
    startTime.toString
  )(db.run(getExtShowIdsByProviderChannelAndStartTimeQuery(
    providerChannelId,
    startTime
  ).result))()

  def getShowFromAiring(airingId: String)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[ShowDetailed]] = cache(
    "getShowFromAiring", airingId
  )(db.run((for {
    a <- AiringTable.table if a.airingId === airingId
    ((s, _), series) <- (ShowTable.table
                                  .joinLeft(EpisodeTable.table).on(_.episodeId === _.episodeId)
                                  .joinLeft(SeriesTable.table).on(_._2.map(_.seriesId) === _.seriesId)) if s.showId === a.showId
    (_, se) <- ShowTable.table.joinLeft(SportEventTable.table).on(_.sportEventId === _.sportEventId)
  } yield (s, series, se)).result.headOption).map {
    case Some((s: Show, series: Option[Series], se: Option[SportEvent])) => Some(ShowDetailed(
      `type` = s.`type`,
      title = s.title,
      description = s.description,
      seriesTitle = series.map(_.seriesName),
      sportEventTitle = se.map(_.sportEventTitle),
      originalAirDate = s.originalAirDate,
    ))
    case _ => Option.empty
  })()

  private[this] def addAiringForId(a: Airing)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[String] = cache(
    "addAiringForId", a.airingId
  )(db.run(
    sql"""SELECT insert_or_get_airing_id(${a.airingId}, ${a.showId}, ${a.startTime}, ${a.endTime}, ${a.duration})"""
      .as[String].head
  ))()

  def addOrGetAiring(airing: Airing)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Airing] = cache(
    "addOrGetAiring",
    airing.airingId
  )(for {
    exists <- getAiring(airing.airingId)()
    a <- exists.fold(for {
      airingId <- addAiringForId(airing)
      selected <- getAiring(airingId)(bust = true).map(_.get)
    } yield selected)(Future.successful)
  } yield a)()

  def getAiringStatus(airingId: String)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[AiringStatus]] = cache(
    "getAiringStatus", airingId
  )(db.run(
    sql"""
       SELECT  a.airing_id, a.show_id, a.start_time, a.end_time, a.duration, a.is_new, CASE
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
      """.as[AiringStatus].headOption
  ))()

  private[this] def getAiringFromGracenoteQuery(convert: AiringConversion) =
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

  def getAiringFromGracenote(convert: AiringConversion)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[String]] = cache(
    "getAiringFromGracenote",
    convert.extChannelId,
    convert.extShowId,
    convert.provider,
    convert.startTime.toString
  )(db.run(getAiringFromGracenoteQuery(convert).headOption))()

  def getAiringsFromGracenote(converts: Seq[AiringConversion])(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[String]] = cache(
    "getAiringsFromGracenote",
    converts.map(c => s"${c.extChannelId}|${c.extShowId}|${c.provider}|${c.startTime.toString}").mkString("|")
  )(Future.sequence(
    converts.map(c =>
      getAiringFromGracenote(c)
    )
  ).map(_.filter(_.isDefined).map(_.get)))()
}
