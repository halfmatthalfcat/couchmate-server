package com.couchmate.migration.migrations

import com.couchmate.common.tables.SportEventTeamTable
import com.couchmate.migration.db.MigrationItem

object SportEventTeamMigrations {
  val init = MigrationItem(31L, SportEventTeamTable.table)(
    _.create.addColumns(
      _.sportEventId,
      _.sportTeamId,
      _.isHome
    ).addPrimaryKeys(
      _.sportEventTeamTablePk
    ).addForeignKeys(
      _.sportEventFk,
      _.sportTeamFk
    )
  )()
}
