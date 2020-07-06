package com.couchmate.migration.migrations

import com.couchmate.common.tables.JournalTable
import com.couchmate.migration.db.MigrationItem

object JournalMigrations {
  val init = MigrationItem(23L, JournalTable.table)(
    _.create.addColumns(
      _.ordering,
      _.persistenceId,
      _.sequenceNumber,
      _.deleted,
      _.tags,
      _.message
    ).addPrimaryKeys(
      _.pk
    )
  )()
}
