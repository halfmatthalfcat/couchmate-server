package com.couchmate.migration.migrations

import com.couchmate.common.tables.SportEventTable
import com.couchmate.migration.db.MigrationItem

object SportEventMigrations {

  val init = MigrationItem(11L, SportEventTable.table)(
    _.create.addColumns(
      _.sportEventId,
      _.sportOrganizationId,
      _.sportEventTitle,
    ).addForeignKeys(
      _.sportOrganizationFk,
    ).addIndexes(
      _.sportEventUniqueIdx,
    )
  )()

}
