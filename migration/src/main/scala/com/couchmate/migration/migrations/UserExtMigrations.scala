package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserExtTable
import com.couchmate.migration.db.MigrationItem

object UserExtMigrations {

  val init = MigrationItem(18L, UserExtTable.table)(
    _.create.addColumns(
      _.userId,
      _.extType,
      _.extId,
    ).addForeignKeys(
      _.userFk,
    )
  )()

}
