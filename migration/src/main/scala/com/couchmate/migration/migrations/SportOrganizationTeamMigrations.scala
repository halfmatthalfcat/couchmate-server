package com.couchmate.migration.migrations

import com.couchmate.common.tables.SportOrganizationTeamTable
import com.couchmate.migration.db.MigrationItem

object SportOrganizationTeamMigrations {
  val init = MigrationItem(47L, SportOrganizationTeamTable.table)(
    _.create.addColumns(
      _.sportOrganizationTeamId,
      _.sportTeamId,
      _.sportOrganizationId
    ).addForeignKeys(
      _.sportTeamFk,
      _.sportOrgFk
    ).addIndexes(
      _.teamOrgIdx
    )
  )()
}
