package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserNotificationTeamTable
import com.couchmate.migration.db.MigrationItem

object UserNotificationTeamMigrations {
  val init = MigrationItem(36L, UserNotificationTeamTable.table)(
    _.create.addColumns(
      _.userId,
      _.teamId,
      _.hash,
      _.onlyNew,
      _.created
    ).addPrimaryKeys(
      _.pk
    ).addForeignKeys(
      _.userIdFk,
      _.teamIdFkOld
    )
  )()

  val addSportOrgTeamFk = MigrationItem(50L, UserNotificationTeamTable.table)(
    _.dropForeignKeys(
      _.teamIdFkOld
    ).addForeignKeys(
      _.teamIdFk
    )
  )()

  val addChannelProviderAndActive = MigrationItem(42L, UserNotificationTeamTable.table)(
    _.addColumns(
      _.providerId,
      _.active
    ).addForeignKeys(
      _.providerChannelFk
    )
  )()

  val addName = MigrationItem(45L, UserNotificationTeamTable.table)(
    _.addColumns(
      _.name
    )
  )()
}
