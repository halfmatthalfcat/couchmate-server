package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserWordBlockTable
import com.couchmate.migration.db.MigrationItem

object UserWordBlockMigrations {

  val init = MigrationItem(28L, UserWordBlockTable.table)(
    _.create.addColumns(
      _.userId,
      _.word,
    ).addPrimaryKeys(
      _.userWordBlockPK
    )
  )()

}
