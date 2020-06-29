package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserTable
import com.couchmate.migration.db.MigrationItem

object UserMigrations {

  val init = MigrationItem(1L, UserTable.table)(
    _.create.addColumns(
      _.userId,
      _.role,
      _.active,
      _.verified,
      _.created
    )
  )()

}
