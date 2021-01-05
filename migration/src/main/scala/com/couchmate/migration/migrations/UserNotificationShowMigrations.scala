package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserNotificationShowTable
import com.couchmate.migration.db.MigrationItem

object UserNotificationShowMigrations {
  val init = MigrationItem(34L, UserNotificationShowTable.table)(
    _.create.addColumns(
      _.userId,
      _.airingId,
      _.hash,
      _.created
    ).addPrimaryKeys(
      _.pk
    ).addForeignKeys(
      _.userIdFk,
      _.airingIdFk
    )
  )()
}
