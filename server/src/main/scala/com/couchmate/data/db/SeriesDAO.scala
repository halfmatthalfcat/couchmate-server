package com.couchmate.data.db

import com.couchmate.common.models.Series

class SeriesDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  private[this] implicit val seriesInsertMeta =
    insertMeta[Series](_.seriesId)

  def getSeries(seriesId: Long) = ctx.run(quote {
    query[Series]
      .filter(_.seriesId.contains(seriesId))
  }).headOption

  def getSeriesByExt(extId: Long) = ctx.run(quote {
    query[Series]
      .filter(_.extId == extId)
  }).headOption

  def upsertSeries(series: Series) = ctx.run(quote {
    query[Series]
      .insert(lift(series))
      .onConflictUpdate(_.seriesId)(
        (from, to) => from.extId -> to.extId,
        (from, to) => from.seriesName -> to.seriesName,
        (from, to) => from.totalEpisodes -> to.totalEpisodes,
        (from, to) => from.totalSeasons -> to.totalSeasons,
      ).returning(s => s)
  })

}
