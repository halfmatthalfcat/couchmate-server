package com.couchmate.migration.migrations

import com.couchmate.common.tables.SnapshotTable
import com.couchmate.migration.db.MigrationItem

object SnapshotMigrations {
  val init = MigrationItem(24L, SnapshotTable.table)(
    _.create.addColumns(
      _.persistenceId,
      _.sequenceNumber,
      _.created,
      _.snapshot
    ).addPrimaryKeys(
      _.pk
    )
  )()
}
