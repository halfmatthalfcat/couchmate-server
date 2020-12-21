package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{Episode, Series, Show}
import com.couchmate.common.tables.{EpisodeTable, SeriesTable}

import scala.concurrent.{ExecutionContext, Future}

trait EpisodeDAO {

  def getEpisode(episodeId: Long)(
    implicit
    db: Database
  ): Future[Option[Episode]] =
    db.run(EpisodeDAO.getEpisode(episodeId))

  def getEpisode$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Episode], NotUsed] =
    Slick.flowWithPassThrough(EpisodeDAO.getEpisode)

  def upsertEpisode(episode: Episode)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Episode] =
    db.run(EpisodeDAO.upsertEpisode(episode))

  def upsertEpisode$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[Episode, Episode, NotUsed] =
    Slick.flowWithPassThrough(EpisodeDAO.upsertEpisode)

  def getOrAddEpisode(
    show: Show,
    series: Series,
    episode: Episode
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Show] = (for {
    series <- db.run(SeriesDAO.addOrGetSeries(series).head) recoverWith {
      case ex: Throwable =>
        System.out.println(s"Get series failed: ${ex.getMessage}, ${SeriesDAO.addOrGetSeries(series).statements.mkString("\n")}")
        for {
          s <- db.run(SeriesDAO.addOrGetSeries(series).headOption)
          _ = System.out.println(s"Legit no series: ${s.isEmpty}")
          f <- Future.failed(ex)
        } yield f
    }
    episode <- db.run(EpisodeDAO.addOrGetEpisode(episode.copy(
      seriesId = series.seriesId
    )).head) recoverWith {
      case ex: Throwable =>
        System.out.println(s"Get episode failed: ${ex.getMessage}")
        Future.failed(ex)
    }
    show <- db.run(ShowDAO.addOrGetShow(show.copy(
      episodeId = episode.episodeId
    )).head) recoverWith {
      case ex: Throwable =>
        System.out.println(s"Get show failed: ${ex.getMessage}")
        Future.failed(ex)
    }
  } yield show) recoverWith {
    case ex: Throwable =>
      System.out.println(s"!Episode failed ${ex.getMessage}")
      Future.failed(ex)
  }
}

object EpisodeDAO {
  private[this] lazy val getEpisodeQuery = Compiled { (episodeId: Rep[Long]) =>
    EpisodeTable.table.filter(_.episodeId === episodeId)
  }

  private[common] def getEpisode(episodeId: Long): DBIO[Option[Episode]] =
    getEpisodeQuery(episodeId).result.headOption

  private[this] lazy val getEpisodeForSeriesQuery = Compiled {
    (seriesId: Rep[Long], season: Rep[Option[Long]], episode: Rep[Option[Long]]) => for {
      s <- SeriesTable.table if s.seriesId === seriesId
      e <- EpisodeTable.table if (
        e.season === season &&
        e.episode === episode &&
        e.seriesId === s.seriesId
      )
    } yield e
  }

  private[common] def getEpisodeForSeries(
    seriesId: Long,
    season: Option[Long],
    episode: Option[Long]
  ): DBIO[Option[Episode]] =
    getEpisodeForSeriesQuery(seriesId, season, episode).result.headOption

  private[common] def upsertEpisode(episode: Episode)(
    implicit
    ec: ExecutionContext
  ): DBIO[Episode] =
    episode.episodeId.fold[DBIO[Episode]](
      (EpisodeTable.table returning EpisodeTable.table) += episode
    ) { (episodeId: Long) => for {
      _ <- EpisodeTable
        .table
        .filter(_.episodeId === episodeId)
        .update(episode)
      updated <- EpisodeDAO.getEpisode(episodeId)
    } yield updated.get}

  private[common] def addOrGetEpisode(e: Episode) =
    sql"""
         WITH input_rows(series_id, season, episode) AS (
          VALUES (${e.seriesId}, ${e.season}, ${e.episode})
         ), ins AS (
          INSERT INTO episode as e (series_id, season, episode)
          SELECT * FROM input_rows
          ON CONFLICT (series_id, season, episode) DO NOTHING
          RETURNING episode_id, series_id, season, episode
         ), sel AS (
          SELECT episode_id, series_id, season, episode
          FROM ins
          UNION ALL
          SELECT e.episode_id, series_id, season, episode
          FROM input_rows
          JOIN episode as e USING (series_id, season, episode)
         ), ups AS (
           INSERT INTO episode AS ep (series_id, season, episode)
           SELECT i.*
           FROM   input_rows i
           LEFT   JOIN sel   s USING (series_id, season, episode)
           WHERE  s.series_id IS NULL
           ON     CONFLICT (series_id, season, episode) DO UPDATE
           SET    series_id = excluded.series_id,
                  season = excluded.season,
                  episode = excluded.episode
           RETURNING episode_id, series_id, season, episode
         )  SELECT episode_id, series_id, season, episode FROM sel
            UNION  ALL
            TABLE  ups;
         """.as[Episode]

}
