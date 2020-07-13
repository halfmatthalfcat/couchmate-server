package com.couchmate.migration.migrations

import com.couchmate.common.tables.EpisodeTable
import com.couchmate.migration.db.MigrationItem

object EpisodeMigrations {

  val init = MigrationItem(9L, EpisodeTable.table)(
    _.create.addColumns(
      _.episodeId,
      _.seriesId,
      _.season,
      _.episode,
    ).addForeignKeys(
      _.seriesFk,
    ).addIndexes(
      _.seriesIdx
    )
  )()

}
