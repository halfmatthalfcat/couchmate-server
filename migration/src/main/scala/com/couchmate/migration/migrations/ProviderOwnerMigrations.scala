package com.couchmate.migration.migrations

import com.couchmate.common.dao.ProviderOwnerDAO
import com.couchmate.common.models.data.ProviderOwner
import com.couchmate.common.tables.ProviderOwnerTable
import com.couchmate.migration.db.MigrationItem

import scala.concurrent.ExecutionContext

object ProviderOwnerMigrations {

  def init(implicit ec: ExecutionContext) = MigrationItem(2L, ProviderOwnerTable.table)(
    _.create.addColumns(
      _.providerOwnerId,
      _.extProviderOwnerId,
      _.name,
    )
  )(
    ProviderOwnerDAO.upsertProviderOwner(ProviderOwner(
      providerOwnerId = None,
      extProviderOwnerId = None,
      name = "Default"
    ))
  )

}
