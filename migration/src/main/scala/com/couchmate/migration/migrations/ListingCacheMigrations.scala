package com.couchmate.migration.migrations

import com.couchmate.common.tables.ListingCacheTable
import com.couchmate.migration.db.MigrationItem

object ListingCacheMigrations {

  val init = MigrationItem(21L, ListingCacheTable.table)(
    _.create.addColumns(
      _.listingCacheId,
      _.providerChannelId,
      _.startTime,
      _.airings,
    ).addForeignKeys(
      _.providerChannelFk,
    )
  )()

  val providerStartTimeIdx = MigrationItem(52L, ListingCacheTable.table)(
    _.addIndexes(
      _.idx
    )
  )()

}
