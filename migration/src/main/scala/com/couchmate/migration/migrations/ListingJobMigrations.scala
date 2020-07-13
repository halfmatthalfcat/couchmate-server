package com.couchmate.migration.migrations

import com.couchmate.common.tables.ListingJobTable
import com.couchmate.migration.db.MigrationItem

object ListingJobMigrations {
  val init = MigrationItem(25L, ListingJobTable.table)(
    _.create.addColumns(
      _.listingJobId,
      _.providerId,
      _.pullAmount,
      _.started,
      _.completed,
      _.baseSlot,
      _.lastSlot,
      _.status
    ).addForeignKeys(
      _.providerFk
    )
  )()
}
