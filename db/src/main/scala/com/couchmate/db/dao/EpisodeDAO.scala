package com.couchmate.db.dao

import com.couchmate.common.models.Episode
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.EpisodeQueries
import com.couchmate.db.table.EpisodeTable

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
