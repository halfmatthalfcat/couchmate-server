package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserActivityTable
import com.couchmate.migration.db.MigrationItem

object UserActivityMigrations {

  val init = MigrationItem(17L, UserActivityTable.table)(
    _.create.addColumns(
      _.userId,
      _.action,
      _.created,
    ).addForeignKeys(
      _.userFk,
    )
  )()

}
