package com.couchmate.migration.migrations

import com.couchmate.common.tables.ChannelOwnerTable
import com.couchmate.migration.db.MigrationItem

object ChannelOwnerMigrations {

  val init = MigrationItem(5L, ChannelOwnerTable.table)(
    _.create.addColumns(
      _.channelOwnerId,
      _.extId,
      _.callsign
    )
  )()

  val extId = MigrationItem(61L, ChannelOwnerTable.table)(
    _.addIndexes(
      _.extIdx
    )
  )()

}
