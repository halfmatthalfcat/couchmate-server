package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserProviderTable
import com.couchmate.migration.db.MigrationItem

object UserProviderMigrations {

  val init = MigrationItem(4L, UserProviderTable.table)(
    _.create.addColumns(
      _.userId,
      _.providerId,
    ).addForeignKeys(
      _.userFk,
      _.providerFk,
    )
  )()

}
