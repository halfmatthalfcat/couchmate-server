package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserNotificationTeamTable
import com.couchmate.migration.db.MigrationItem

object UserNotificationTeamMigrations {
  val init = MigrationItem(36L, UserNotificationTeamTable.table)(
    _.create.addColumns(
      _.userId,
      _.teamId
    ).addPrimaryKeys(
      _.pk
    ).addForeignKeys(
      _.userIdFk,
      _.teamIdFk
    )
  )
}
