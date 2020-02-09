package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.EpisodeTable
import com.couchmate.data.models.Episode
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

class EpisodeDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getEpisode(episodeId: Long): Future[Option[Episode]] = {
    db.run(EpisodeDAO.getEpisode(episodeId).result.headOption)
  }

  def upsertEpisode(episode: Episode): Future[Episode] = db.run(
    episode.episodeId.fold[DBIO[Episode]](
      (EpisodeTable.table returning EpisodeTable.table) += episode
    ) { (episodeId: Long) => for {
      _ <- EpisodeTable.table.update(episode)
      updated <- EpisodeDAO.getEpisode(episodeId).result.head
    } yield updated}.transactionally
  )
}

object EpisodeDAO {
  private[dao] lazy val getEpisode = Compiled { (episodeId: Rep[Long]) =>
    EpisodeTable.table.filter(_.episodeId === episodeId)
  }
}
