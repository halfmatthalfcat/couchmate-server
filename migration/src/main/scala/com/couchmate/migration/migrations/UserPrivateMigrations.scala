package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserPrivateTable
import com.couchmate.migration.db.MigrationItem

object UserPrivateMigrations {

  val init = MigrationItem(19L, UserPrivateTable.table)(
    _.create.addColumns(
      _.userId,
      _.password,
    ).addForeignKeys(
      _.userFk,
    )
  )()

}
