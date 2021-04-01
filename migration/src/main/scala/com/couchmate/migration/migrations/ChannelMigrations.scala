package com.couchmate.migration.migrations

import com.couchmate.common.tables.ChannelTable
import com.couchmate.migration.db.MigrationItem

object ChannelMigrations {

  val init = MigrationItem(6L, ChannelTable.table)(
    _.create.addColumns(
      _.channelId,
      _.extId,
      _.channelOwnerId,
      _.callsign
    ).addForeignKeys(
      _.channelOwnerIdFk
    )
  )()

  val extIdIdx = MigrationItem(62L, ChannelTable.table)(
    _.addIndexes(
      _.channelExtIdIdx
    )
  )()

}
