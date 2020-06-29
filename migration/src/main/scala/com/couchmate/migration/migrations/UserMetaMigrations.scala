package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserMetaTable
import com.couchmate.migration.db.MigrationItem

object UserMetaMigrations {

  val init = MigrationItem(16L, UserMetaTable.table)(
    _.create.addColumns(
      _.userId,
      _.username,
      _.email
    ).addForeignKeys(
      _.userFk,
    )
  )()

}
