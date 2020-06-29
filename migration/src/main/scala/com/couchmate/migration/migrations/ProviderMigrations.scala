package com.couchmate.migration.migrations

import com.couchmate.common.dao.ProviderDAO
import com.couchmate.common.models.data.Provider
import com.couchmate.common.tables.ProviderTable
import com.couchmate.migration.db.MigrationItem

import scala.concurrent.ExecutionContext

object ProviderMigrations {

  def init(implicit ec: ExecutionContext) = MigrationItem(3L, ProviderTable.table)(
    _.create.addColumns(
      _.providerId,
      _.providerOwnerId,
      _.extId,
      _.name,
      _.`type`,
      _.location,
    ).addForeignKeys(
      _.sourceFK
    ).addIndexes(
      _.sourceIdx
    )
  )(
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = Some(1L),
      extId = "USA-DFLTE",
      name = "Eastern US Default",
      `type` = "Default",
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = Some(1L),
      extId = "USA-DFLTC",
      name = "Central US Default",
      `type` = "Default",
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = Some(1L),
      extId = "USA-DFLTM",
      name = "Mountain US Default",
      `type` = "Default",
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = Some(1L),
      extId = "USA-DFLTP",
      name = "Pacific US Default",
      `type` = "Default",
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = Some(1L),
      extId = "USA-DFLTH",
      name = "Hawaii US Default",
      `type` = "Default",
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = Some(1L),
      extId = "USA-DFLTA",
      name = "Alaska US Default",
      `type` = "Default",
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = Some(1L),
      extId = "CAN-DFLTEC",
      name = "Eastern Canada Default",
      `type` = "Default",
      location = Some("Canada")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = Some(1L),
      extId = "CAN-DFLTCC",
      name = "Central Canada Default",
      `type` = "Default",
      location = Some("Canada")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = Some(1L),
      extId = "CAN-DFLTMC",
      name = "Mountain Canada Default",
      `type` = "Default",
      location = Some("Canada")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = Some(1L),
      extId = "CAN-DFLTPC",
      name = "Pacific Canada Default",
      `type` = "Default",
      location = Some("Canada")
    ))
  )

}
