package com.couchmate.migration.migrations

import com.couchmate.common.tables.ProviderChannelTable
import com.couchmate.migration.db.MigrationItem

object ProviderChannelMigrations {

  val init = MigrationItem(7L, ProviderChannelTable.table)(
    _.create.addColumns(
      _.providerChannelId,
      _.providerId,
      _.channelId,
      _.channel,
    ).addForeignKeys(
      _.providerFk,
      _.channelFk,
    ).addIndexes(
      _.providerChannelIdx,
    )
  )()

}
