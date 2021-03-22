package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserChannelFavoriteTable
import com.couchmate.migration.db.MigrationItem

object UserChannelFavoriteMigrations {
  val init = MigrationItem(58L, UserChannelFavoriteTable.table)(
    _.create.addColumns(
      _.userId,
      _.providerChannelId
    ).addForeignKeys(
      _.userFk,
      _.providerChannelFk
    )
  )()
}
