package com.couchmate.data.db

import com.couchmate.common.models.Episode

import scala.concurrent.ExecutionContext

class EpisodeDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  private[this] implicit val episodeInsertMeta =
    insertMeta[Episode](_.episodeId)

  def getEpisode(episodeId: Long)(implicit ec: ExecutionContext) = ctx.run(quote {
    query[Episode]
      .filter(_.episodeId.contains(episodeId))
  })

  def upsertEpisode(episode: Episode) = ctx.run(quote {
    query[Episode]
      .insert(lift(episode))
      .onConflictUpdate(_.episodeId)(
        (from, to) => from.seriesId -> to.seriesId,
        (from, to) => from.episode -> to.episode,
        (from, to) => from.season -> to.season
      ).returning(e => e)
  })

}
