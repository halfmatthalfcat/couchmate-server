package com.couchmate.migration.migrations

import com.couchmate.common.tables.AiringTable
import com.couchmate.migration.db.MigrationItem

object AiringMigrations {

  val init = MigrationItem(13L, AiringTable.table)(
    _.create.addColumns(
      _.airingId,
      _.showId,
      _.startTime,
      _.endTime,
      _.duration,
    ).addForeignKeys(
      _.showFk,
    ).addIndexes(
      _.showStartTimeIdx,
      _.startTimeIdx,
      _.endTimeIdx,
    )
  )()

 val addIsNew = MigrationItem(29L, AiringTable.table)(
   _.addColumns(
     _.isNew
   )
 )()

  val startTimeEndTimeIdx = MigrationItem(53L, AiringTable.table)(
    _.addIndexes(
      _.startTimeEndTimeIdx
    )
  )()
}
