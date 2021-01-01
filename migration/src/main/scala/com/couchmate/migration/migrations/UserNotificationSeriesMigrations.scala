package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserNotificationSeriesTable
import com.couchmate.migration.db.MigrationItem

object UserNotificationSeriesMigrations {
  val init = MigrationItem(35L, UserNotificationSeriesTable.table)(
    _.create.addColumns(
      _.userId,
      _.seriesId,
    ).addPrimaryKeys(
      _.pk
    ).addForeignKeys(
      _.userIdFk,
      _.seriesIdFk
    )
  )()
}
