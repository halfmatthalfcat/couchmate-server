package com.couchmate.data.db

import com.couchmate.common.models.Show

class ShowDAO()(
  implicit
  val ctx: CMContext,
) {
  import ctx._

  private[this] implicit val showInsertMeta =
    insertMeta[Show](_.showId)

  def getShow(showId: Long) = quote {
    query[Show]
      .filter(_.showId.contains(showId))
  }

  def getShowFromExt(extId: Long) = quote {
    query[Show]
      .filter(_.extId == extId)
  }

  def upsertShow(show: Show) = quote {
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
  }

}
