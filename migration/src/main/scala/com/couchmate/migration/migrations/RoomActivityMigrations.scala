package com.couchmate.migration.migrations

import com.couchmate.common.tables.RoomActivityTable
import com.couchmate.migration.db.MigrationItem

object RoomActivityMigrations {

  val init = MigrationItem(15L, RoomActivityTable.table)(
    _.create.addColumns(
      _.airingId,
      _.userId,
      _.action,
      _.created,
    ).addForeignKeys(
      _.airingFk,
      _.userFk,
    ).addIndexes(
      _.roomActivityIdx,
    )
  )()

}
