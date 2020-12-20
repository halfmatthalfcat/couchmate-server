package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserNotificationConfigurationTable
import com.couchmate.migration.db.MigrationItem

object UserNotificationConfigurationMigrations {
  val init = MigrationItem(32L, UserNotificationConfigurationTable.table)(
    _.create.addColumns(
      _.userId,
      _.platform,
      _.active,
      _.token
    ).addPrimaryKeys(
      _.pk
    ).addForeignKeys(
      _.userIdFk
    )
  )()
}
