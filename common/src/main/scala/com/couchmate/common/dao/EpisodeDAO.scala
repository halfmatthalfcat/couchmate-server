package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{Episode, Series, Show}
import com.couchmate.common.tables.{EpisodeTable, SeriesTable}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object EpisodeDAO {
  private[this] lazy val getEpisodeQuery = Compiled { (episodeId: Rep[Long]) =>
    EpisodeTable.table.filter(_.episodeId === episodeId)
  }

  def getEpisode(episodeId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Episode]] = cache(
    "getEpisode", episodeId
  )(db.run(getEpisodeQuery(episodeId).result.headOption))()

  private[this] lazy val getEpisodeForSeriesQuery = Compiled {
    (seriesId: Rep[Long], season: Rep[Long], episode: Rep[Long]) => for {
      s <- SeriesTable.table if s.seriesId === seriesId
      e <- EpisodeTable.table if (
        e.season === season &&
        e.episode === episode &&
        e.seriesId === s.seriesId
      )
    } yield e
  }

  def getEpisodeForSeries(
    seriesId: Long,
    season: Long,
    episode: Long
  )(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Episode]] = cache(
    "getEpisodeForSeries",
    seriesId,
    season,
    episode
  )(db.run(getEpisodeForSeriesQuery(seriesId, season, episode).result.headOption))(
    bust = bust
  )

  private[this] def addEpisodeForId(e: Episode)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addEpisodeForId",
    e.seriesId,
    e.season,
    e.episode
  )(db.run(
    sql"""SELECT insert_or_get_episode_id(${e.seriesId}, ${e.season}, ${e.episode})"""
      .as[Long].head
  ))()

  def addOrGetEpisode(e: Episode)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Episode] = cache(
    "addOrGetEpisode",
    e.seriesId,
    e.season,
    e.episode
  )(for {
    exists <- getEpisodeForSeries(
      e.seriesId.get, e.season, e.episode
    )()
    e <- exists.fold(for {
      _ <- addEpisodeForId(e)
      selected <- getEpisodeForSeries(
        e.seriesId.get, e.season, e.episode
      )(bust = true).map(_.get)
    } yield selected)(Future.successful)
  } yield e)()

  def addOrGetShow(
    show: Show,
    series: Series,
    episode: Episode
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Show] = for {
    series <- SeriesDAO.addOrGetSeries(series) recoverWith {
      case ex: Throwable =>
        System.out.println(s"Series Error: ${ex.getMessage}")
        Future.failed(ex)
    }
    episode <- addOrGetEpisode(episode.copy(
      seriesId = series.seriesId
    )) recoverWith {
      case ex: Throwable =>
        System.out.println(s"Episode Error: ${ex.getMessage}")
        Future.failed(ex)
    }
    show <- ShowDAO.addOrGetShow(show.copy(
      episodeId = episode.episodeId
    )) recoverWith {
      case ex: Throwable =>
        System.out.println(s"Show Error: ${ex.getMessage}")
        Future.failed(ex)
    }
  } yield show

}
