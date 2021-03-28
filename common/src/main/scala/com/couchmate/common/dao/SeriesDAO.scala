package com.couchmate.common.dao

import java.time.{LocalDateTime, ZoneId}
import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.api.grid.GridSeries
import com.couchmate.common.models.data.{Airing, Series}
import com.couchmate.common.tables.{AiringTable, EpisodeTable, LineupTable, SeriesTable, ShowTable}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object SeriesDAO {
  private[this] lazy val getSeriesQuery = Compiled { (seriesId: Rep[Long]) =>
    SeriesTable.table.filter(_.seriesId === seriesId)
  }

  def getSeries(seriesId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Series]] = cache(
    "getSeries",
    seriesId
  )(db.run(getSeriesQuery(seriesId).result.headOption))()

  private[this] lazy val getSeriesByExtQuery = Compiled { (extId: Rep[Long]) =>
    SeriesTable.table.filter(_.extId === extId)
  }

  def getSeriesByExt(extId: Long)(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Series]] = cache(
    "getSeriesByExt",
    extId
  )(db.run(getSeriesByExtQuery(extId).result.headOption))(bust = bust)

  def getSeriesByEpisode(episodeId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Series]] = cache(
    "getSeriesByEpisode",
    episodeId
  )(for {
    exists <- EpisodeDAO.getEpisode(episodeId)
    series <- exists.fold(
      Future.successful(Option.empty[Series])
    )(e => getSeries(e.seriesId.get))
  } yield series)()

  def getUpcomingSeriesAirings(
    seriesId: Long,
    providerChannelId: Long
  )(
    implicit
    db: Database
  ): Future[Seq[Airing]] = db.run((for {
    e <- EpisodeTable.table if e.seriesId === seriesId
    s <- ShowTable.table if s.episodeId === e.episodeId
    a <- AiringTable.table if (
      a.showId === s.showId &&
        a.startTime >= LocalDateTime.now(ZoneId.of("UTC"))
      )
    l <- LineupTable.table if (
      a.airingId === l.airingId &&
        l.providerChannelId === providerChannelId
      )
  } yield a).result)

  def getAllGridSeries(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[GridSeries]] = cache(
    "getAllGridSeries"
  )(db.run(
    sql"""SELECT
            s.series_id, s.series_name,
            e.episode_id, e.season, e.episode,
            COALESCE(seriesFollows.following, 0) as following
          FROM episode as e
          JOIN series as s
          ON e.series_id = s.series_id
          LEFT JOIN (
            SELECT    series_id, count(*) as following
            FROM      user_notification_series
            GROUP BY  series_id
          ) as seriesFollows
          ON seriesFollows.series_id = s.series_id
         """.as[GridSeries]
  ))()

  def getGridSeries(episodeId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[GridSeries]] = cache(
    "getGridSeries",
    episodeId
  )(db.run(
    sql"""SELECT
            s.series_id, s.series_name,
            e.episode_id, e.season, e.episode,
            COALESCE(seriesFollows.following, 0) as following
          FROM episode as e
          JOIN series as s
          ON e.series_id = s.series_id
          LEFT JOIN (
            SELECT    series_id, count(*) as following
            FROM      user_notification_series
            GROUP BY  series_id
          ) as seriesFollows
          ON seriesFollows.series_id = s.series_id
          WHERE e.episode_id = ${episodeId}
         """.as[GridSeries].headOption
  ))()

  private[this] def addSeriesForId(s: Series)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addSeriesForId",
    s.extId
  )(db.run(
    sql"""SELECT insert_or_get_series_id(${s.extId}, ${s.seriesName}, ${s.totalSeasons}, ${s.totalEpisodes})"""
      .as[Long].head
  ))()


  def addOrGetSeries(s: Series)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Series] = cache(
    "addOrGetSeries",
    s.extId
  )(for {
    exists <- getSeriesByExt(s.extId)()
    s <- exists.fold(for {
      _ <- addSeriesForId(s)
      selected <- getSeriesByExt(s.extId)(bust = true)
        .map(_.get)
    } yield selected)(Future.successful)
  } yield s)()

}
