package com.couchmate.migration.migrations

import com.couchmate.common.tables.ZipProviderTable
import com.couchmate.migration.db.MigrationItem

object ZipProviderMigrations {

  val init = MigrationItem(14L, ZipProviderTable.table)(
    _.create.addColumns(
      _.zipCode,
      _.countryCode,
      _.providerId,
    ).addPrimaryKeys(
      _.zipProviderPk,
    ).addForeignKeys(
      _.providerFk,
    )
  )()

}
