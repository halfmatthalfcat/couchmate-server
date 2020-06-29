package com.couchmate.migration.migrations

import com.couchmate.common.tables.SportOrganizationTable
import com.couchmate.migration.db.MigrationItem

object SportOrganizationMigrations {

  val init = MigrationItem(10L, SportOrganizationTable.table)(
    _.create.addColumns(
      _.sportOrganizationId,
      _.extSportId,
      _.extOrgId,
      _.sportName,
      _.orgName,
    ).addIndexes(
      _.sourceExtSportOrgIdx,
    )
  )()

}
