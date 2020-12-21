package com.couchmate.migration.migrations

import com.couchmate.common.tables.SportTeamTable
import com.couchmate.migration.db.MigrationItem

object SportTeamMigrations {
  val init = MigrationItem(30L, SportTeamTable.table)(
    _.create.addColumns(
      _.sportTeamId,
      _.extSportTeamId,
      _.name
    )
  )()

  val uniqueExtIndex = MigrationItem(33L, SportTeamTable.table)(
    _.addIndexes(
      _.uniqueExtId
    )
  )()
}
