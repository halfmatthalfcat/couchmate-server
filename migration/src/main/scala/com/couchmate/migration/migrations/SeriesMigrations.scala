package com.couchmate.migration.migrations

import com.couchmate.common.tables.SeriesTable
import com.couchmate.migration.db.MigrationItem

object SeriesMigrations {

  val init = MigrationItem(8L, SeriesTable.table)(
    _.create.addColumns(
      _.seriesId,
      _.extId,
      _.seriesName,
      _.totalSeasons,
      _.totalEpisodes,
    ).addIndexes(
      _.extIdIdx
    )
  )()

}
