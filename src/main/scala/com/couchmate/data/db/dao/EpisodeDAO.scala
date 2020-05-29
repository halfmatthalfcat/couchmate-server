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
}

object EpisodeDAO {
  private[this] lazy val getEpisodeQuery = Compiled { (episodeId: Rep[Long]) =>
    EpisodeTable.table.filter(_.episodeId === episodeId)
  }

  private[dao] def getEpisode(episodeId: Long): DBIO[Option[Episode]] =
    getEpisodeQuery(episodeId).result.headOption

  private[dao] def upsertEpisode(episode: Episode)(
    implicit
    ec: ExecutionContext
  ): DBIO[Episode] =
    episode.episodeId.fold[DBIO[Episode]](
      (EpisodeTable.table returning EpisodeTable.table) += episode
    ) { (episodeId: Long) => for {
      _ <- EpisodeTable.table.update(episode)
      updated <- EpisodeDAO.getEpisode(episodeId)
    } yield updated.get}
}
