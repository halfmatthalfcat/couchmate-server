package com.couchmate.data.db

import com.couchmate.common.models.Series

class SeriesDAO()(
  implicit
  val ctx: CMContext,
) {
  import ctx._

  def getSeries(seriesId: Long) = quote {
    query[Series]
      .filter(_.seriesId.contains(seriesId))
  }

  def getSeriesByExt(extId: Long) = quote {
    query[Series]
      .filter(_.extId == extId)
  }

  def upsertSeries(series: Series) = quote {
    query[Series]
      .insert(lift(series))
      .onConflictUpdate(_.seriesId)(
        (from, to) => from.extId -> to.extId,
        (from, to) => from.seriesName -> to.seriesName,
        (from, to) => from.totalEpisodes -> to.totalEpisodes,
        (from, to) => from.totalSeasons -> to.totalSeasons,
      ).returning(s => s)
  }

}
