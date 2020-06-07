package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{EpisodeTable, SeriesTable, ShowTable}
import com.couchmate.data.models.{Episode, Series, Show}
import slick.lifted.Compiled

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
  ): Future[Show] =
    db.run(EpisodeDAO.getOrAddEpisode(show, series, episode))

  def getOrAddEpisode$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[(Show, Series, Episode), Show, NotUsed] =
    Slick.flowWithPassThrough(
      (EpisodeDAO.getOrAddEpisode _).tupled
    )
}

object EpisodeDAO {
  private[this] lazy val getEpisodeQuery = Compiled { (episodeId: Rep[Long]) =>
    EpisodeTable.table.filter(_.episodeId === episodeId)
  }

  private[db] def getEpisode(episodeId: Long): DBIO[Option[Episode]] =
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

  private[db] def getEpisodeForSeries(
    seriesId: Long,
    season: Option[Long],
    episode: Option[Long]
  ): DBIO[Option[Episode]] =
    getEpisodeForSeriesQuery(seriesId, season, episode).result.headOption

  private[db] def upsertEpisode(episode: Episode)(
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

  private[db] def getOrAddEpisode(
    show: Show,
    series: Series,
    episode: Episode
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Show] = (ShowDAO.getShowByShow(show) flatMap {
    case Some(show) => DBIO.successful(show)
    case None => for {
      seriesExists <- series match {
        case Series(Some(seriesId), _, _, _, _) =>
          SeriesDAO.getSeries(seriesId)
        case Series(None, extSeriesId, _, _, _) =>
          SeriesDAO.getSeriesByExt(extSeriesId)
        case _ => DBIO.successful(Option.empty[Series])
      }
      s <- seriesExists.fold(SeriesDAO.upsertSeries(series))(DBIO.successful)
      eExists <- getEpisodeForSeries(
        s.seriesId.get,
        episode.season,
        episode.season
      )
      e <- eExists.fold(upsertEpisode(episode.copy(
        seriesId = s.seriesId
      )))(DBIO.successful)
      s <- ShowDAO.upsertShow(show.copy(
        episodeId = e.episode
      ))
    } yield s
  }).transactionally
}
