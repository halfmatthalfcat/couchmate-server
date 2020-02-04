package com.couchmate.data.db

import com.couchmate.common.models.Show

class ShowDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  private[this] implicit val showInsertMeta =
    insertMeta[Show](_.showId)

  def getShow(showId: Long) = ctx.run(quote {
    query[Show]
      .filter(_.showId.contains(showId))
  }).headOption

  def getShowFromExt(extId: Long) = ctx.run(quote {
    query[Show]
      .filter(_.extId == extId)
  }).headOption

  def upsertShow(show: Show) = ctx.run(quote {
    query[Show]
      .insert(lift(show))
      .onConflictUpdate(_.showId)(
        (from, to) => from.extId -> to.extId,
        (from, to) => from.`type` -> to.`type`,
        (from, to) => from.description -> to.description,
        (from, to) => from.title -> to.title,
        (from, to) => from.episodeId -> to.episodeId,
        (from, to) => from.sportEventId -> to.sportEventId,
      ).returning(s => s)
  })

}
