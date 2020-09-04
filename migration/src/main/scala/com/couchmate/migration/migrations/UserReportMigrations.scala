package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserReportTable
import com.couchmate.migration.db.MigrationItem

object UserReportMigrations {

  val init = MigrationItem(27L, UserReportTable.table)(
    _.create.addColumns(
      _.userReportId,
      _.created,
      _.reporterId,
      _.reporteeId,
      _.reportType,
      _.message
    ).addForeignKeys(
      _.reporterFK,
      _.reporteeFK
    )
  )()

}
