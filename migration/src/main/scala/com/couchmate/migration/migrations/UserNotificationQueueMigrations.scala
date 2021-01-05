package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserNotificationQueueTable
import com.couchmate.migration.db.MigrationItem

object UserNotificationQueueMigrations {
  val init = MigrationItem(37L, UserNotificationQueueTable.table)(
    _.create.addColumns(
      _.notificationId,
      _.userId,
      _.airingId,
      _.hash,
      _.platform,
      _.deliverAt,
      _.deliveredAt,
      _.success,
      _.read
    ).addIndexes(
      _.uniqueIdx
    ).addForeignKeys(
      _.userIdFk,
      _.airingIdFk
    )
  )()
}
