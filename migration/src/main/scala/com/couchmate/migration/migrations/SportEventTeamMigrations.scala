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
      _.sportEventTeamTablePkOld
    ).addForeignKeys(
      _.sportEventFk,
      _.sportTeamFk
    )
  )()

  val swapOrganizationTeam = MigrationItem(48L, SportEventTeamTable.table)(
    _.dropPrimaryKeys(
      _.sportEventTeamTablePkOld
    ).dropForeignKeys(
      _.sportTeamFk
    ).dropColumns(
      _.sportTeamId
    ).addColumns(
      _.sportOrganizationTeamId
    ).addPrimaryKeys(
      _.sportEventTeamTablePk
    ).addForeignKeys(
      _.sportOrgTeamFk
    )
  )()
}
