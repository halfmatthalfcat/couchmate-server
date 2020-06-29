package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserMuteTable
import com.couchmate.migration.db.MigrationItem

object UserMuteMigrations {

  val init = MigrationItem(20L, UserMuteTable.table)(
    _.create.addColumns(
      _.userId,
      _.userMuteId,
    ).addForeignKeys(
      _.userIdFk,
      _.userMuteIdFk
    )
  )()

}
