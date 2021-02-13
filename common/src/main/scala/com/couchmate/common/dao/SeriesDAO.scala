package com.couchmate.common.dao

import java.time.{LocalDateTime, ZoneId}

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.api.grid.GridSeries
import com.couchmate.common.models.data.{Airing, Series}
import com.couchmate.common.tables.{AiringTable, EpisodeTable, LineupTable, SeriesTable, ShowTable}
import slick.sql.SqlStreamingAction

import scala.concurrent.{ExecutionContext, Future}

trait SeriesDAO {

  def getSeries(seriesId: Long)(
    implicit
    db: Database
  ): Future[Option[Series]] = {
    db.run(SeriesDAO.getSeries(seriesId))
  }

  def getSeries$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Series], NotUsed] =
    Slick.flowWithPassThrough(SeriesDAO.getSeries)

  def getSeriesByExt(extId: Long)(
    implicit
    db: Database
  ): Future[Option[Series]] = {
    db.run(SeriesDAO.getSeriesByExt(extId))
  }

  def getSeriesByExt$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Series], NotUsed] =
    Slick.flowWithPassThrough(SeriesDAO.getSeriesByExt)

  def getSeriesByEpisode(episodeId: Long)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Option[Series]] =
    db.run(SeriesDAO.getSeriesByEpisode(episodeId))

  def getUpcomingSeriesAirings(
    seriesId: Long,
    providerChannelId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Seq[Airing]] =
    db.run(SeriesDAO.getUpcomingSeriesAirings(seriesId, providerChannelId))

  def getGridSeries(episodeId: Long)(implicit db: Database): Future[Option[GridSeries]] =
    db.run(SeriesDAO.getGridSeries(episodeId).headOption)

  def upsertSeries(series: Series)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Series] =
    db.run(SeriesDAO.upsertSeries(series))

  def upsertSeries$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[Series, Series, NotUsed] =
    Slick.flowWithPassThrough(SeriesDAO.upsertSeries)

  def addOrGetSeries(series: Series)(
    implicit
    db: Database
  ): Future[Series] =
    db.run(SeriesDAO.addOrGetSeries(series).head)
}

object SeriesDAO {
  private[this] lazy val getSeriesQuery = Compiled { (seriesId: Rep[Long]) =>
    SeriesTable.table.filter(_.seriesId === seriesId)
  }

  private[common] def getSeries(seriesId: Long): DBIO[Option[Series]] =
    getSeriesQuery(seriesId).result.headOption

  private[this] lazy val getSeriesByExtQuery = Compiled { (extId: Rep[Long]) =>
    SeriesTable.table.filter(_.extId === extId)
  }

  private[common] def getSeriesByExt(extId: Long): DBIO[Option[Series]] =
    getSeriesByExtQuery(extId).result.headOption

  private[common] def getSeriesByEpisode(episodeId: Long)(
    implicit
    ec: ExecutionContext
  ): DBIO[Option[Series]] = for {
    exists <- EpisodeDAO.getEpisode(episodeId)
    series <- exists.fold[DBIO[Option[Series]]](DBIO.successful(Option.empty))(e => getSeries(e.seriesId.get))
  } yield series

  private[common] def getUpcomingSeriesAirings(
    seriesId: Long,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[Airing]] = (for {
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
  } yield a).result

  private[common] def upsertSeries(series: Series)(
    implicit
    ec: ExecutionContext
  ): DBIO[Series] =
    series.seriesId.fold[DBIO[Series]](
      (SeriesTable.table returning SeriesTable.table) += series
    ) { (seriesId: Long) => for {
      _ <- SeriesTable
        .table
        .filter(_.seriesId === seriesId)
        .update(series)
      updated <- SeriesDAO.getSeries(seriesId)
    } yield updated.get}

  private[common] def getGridSeries(episodeId: Long): SqlStreamingAction[Seq[GridSeries], GridSeries, Effect] =
    sql"""SELECT
            s.series_id, s.series_name,
            e.season, e.episode,
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
         """.as[GridSeries]

  private[common] def addOrGetSeries(series: Series) =
    sql"""
         WITH input_rows(ext_id, series_name, total_seasons, total_episodes) AS (
          VALUES (${series.extId}, ${series.seriesName}, ${series.totalSeasons}, ${series.totalEpisodes})
         ), ins AS (
          INSERT INTO series (ext_id, series_name, total_seasons, total_episodes)
          SELECT * FROM input_rows
          ON CONFLICT (ext_id) DO NOTHING
          RETURNING series_id, ext_id, series_name, total_seasons, total_episodes
         ), sel AS (
          SELECT series_id, ext_id, series_name, total_seasons, total_episodes
          FROM ins
          UNION ALL
          SELECT s.series_id, ext_id, s.series_name, s.total_seasons, s.total_episodes
          FROM input_rows
          JOIN series AS s USING (ext_id)
         ), ups AS (
           INSERT INTO series AS srs (ext_id, series_name, total_seasons, total_episodes)
           SELECT i.*
           FROM   input_rows i
           LEFT   JOIN sel   s USING (ext_id)
           WHERE  s.ext_id IS NULL
           ON     CONFLICT (ext_id) DO UPDATE
           SET    series_name = excluded.series_name,
                  total_seasons = excluded.total_seasons,
                  total_episodes = excluded.total_episodes
           RETURNING series_id, ext_id, series_name, total_seasons, total_episodes
         )  SELECT series_id, ext_id, series_name, total_seasons, total_episodes FROM sel
            UNION  ALL
            TABLE  ups;
         """.as[Series]
}
