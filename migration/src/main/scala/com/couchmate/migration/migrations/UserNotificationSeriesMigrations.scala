package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserNotificationSeriesTable
import com.couchmate.migration.db.MigrationItem

object UserNotificationSeriesMigrations {
  val init = MigrationItem(35L, UserNotificationSeriesTable.table)(
    _.create.addColumns(
      _.userId,
      _.seriesId,
      _.hash,
      _.onlyNew,
      _.created
    ).addPrimaryKeys(
      _.pkOld
    ).addForeignKeys(
      _.userIdFk,
      _.seriesIdFk
    )
  )()

  val addChannelProviderAndActive = MigrationItem(40L, UserNotificationSeriesTable.table)(
    _.dropPrimaryKeys(
      _.pkOld
    ).addColumns(
      _.providerChannelId,
      _.active
    ).addPrimaryKeys(
      _.pk
    ).addForeignKeys(
      _.providerChannelFk
    )
  )()

  val addNameAndCallsign = MigrationItem(46L, UserNotificationSeriesTable.table)(
    _.addColumns(
      _.name,
      _.callsign
    )
  )()
}
