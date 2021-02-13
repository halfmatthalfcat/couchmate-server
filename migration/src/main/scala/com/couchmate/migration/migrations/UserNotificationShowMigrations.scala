package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserNotificationShowTable
import com.couchmate.migration.db.MigrationItem

object UserNotificationShowMigrations {
  val init = MigrationItem(34L, UserNotificationShowTable.table)(
    _.create.addColumns(
      _.userId,
      _.airingId,
      _.hash,
      _.onlyNew,
      _.created
    ).addPrimaryKeys(
      _.pkOld
    ).addForeignKeys(
      _.userIdFk,
      _.airingIdFk
    )
  )()

  val addChannelProviderAndActive = MigrationItem(41L, UserNotificationShowTable.table)(
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

  val addNameAndCallsign = MigrationItem(44L, UserNotificationShowTable.table)(
    _.addColumns(
      _.name,
      _.callsign
    )
  )()
}
