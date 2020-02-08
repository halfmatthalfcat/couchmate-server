package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.EpisodeQueries
import com.couchmate.data.db.table.EpisodeTable
import com.couchmate.data.models.Episode

import scala.concurrent.{ExecutionContext, Future}

class EpisodeDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends EpisodeQueries {

  def getEpisode(episodeId: Long): Future[Option[Episode]] = {
    db.run(super.getEpisode(episodeId).result.headOption)
  }

  def upsertEpisode(episode: Episode): Future[Episode] =
    episode.episodeId.fold(
      db.run((EpisodeTable.table returning EpisodeTable.table) += episode)
    ) { (episodeId: Long) => db.run(for {
      _ <- EpisodeTable.table.update(episode)
      updated <- super.getEpisode(episodeId)
    } yield updated.result.head.transactionally)}

}
