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
      _.title,
      _.platform,
      _.token,
      _.deliverAt,
      _.deliveredAt,
      _.success,
      _.read
    ).addIndexes(
      _.uniqueIdxOld
    ).addForeignKeys(
      _.userIdFk,
      _.airingIdFk
    )
  )()

  val addCallsign = MigrationItem(39L, UserNotificationQueueTable.table)(
    _.addColumns(
      _.callsign
    )
  )()

  val addNotificationType = MigrationItem(43L, UserNotificationQueueTable.table)(
    _.dropIndexes(
      _.uniqueIdxOld
    ).addColumns(
      _.notificationType
    ).addIndexes(
      _.uniqueIdx
    )
  )()

  val addReadAt = MigrationItem(51L, UserNotificationQueueTable.table)(
    _.addColumns(
      _.readAt
    )
  )()
}
