package com.couchmate.migration.migrations

import com.couchmate.common.tables.ShowTable
import com.couchmate.migration.db.MigrationItem

object ShowMigrations {

  val init = MigrationItem(12L, ShowTable.table)(
    _.create.addColumns(
      _.showId,
      _.extId,
      _.`type`,
      _.episodeId,
      _.sportEventId,
      _.title,
      _.description,
      _.originalAirDate,
    ).addForeignKeys(
      _.episodeFk,
      _.sportFk,
    )
  )()

}
