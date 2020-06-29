package com.couchmate.migration.migrations

import com.couchmate.common.tables.LineupTable
import com.couchmate.migration.db.MigrationItem

object LineupMigrations {

  val init = MigrationItem(22L, LineupTable.table)(
    _.create.addColumns(
      _.lineupId,
      _.providerChannelId,
      _.airingId,
      _.active,
    ).addForeignKeys(
      _.providerChannelFk,
      _.airingFk,
    )
  )()

}
