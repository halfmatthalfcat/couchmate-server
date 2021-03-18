package com.couchmate.migration.migrations

import com.couchmate.common.dao.ProviderDAO
import com.couchmate.common.models.data.{Provider, ProviderType}
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
      providerOwnerId = 1L,
      extId = "USA-DFLTE",
      name = "Eastern US Default",
      `type` = ProviderType.Default,
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "USA-DFLTC",
      name = "Central US Default",
      `type` = ProviderType.Default,
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "USA-DFLTM",
      name = "Mountain US Default",
      `type` = ProviderType.Default,
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "USA-DFLTP",
      name = "Pacific US Default",
      `type` = ProviderType.Default,
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "USA-DFLTH",
      name = "Hawaii US Default",
      `type` = ProviderType.Default,
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "USA-DFLTA",
      name = "Alaska US Default",
      `type` = ProviderType.Default,
      location = Some("USA")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "CAN-DFLTEC",
      name = "Eastern Canada Default",
      `type` = ProviderType.Default,
      location = Some("Canada")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "CAN-DFLTCC",
      name = "Central Canada Default",
      `type` = ProviderType.Default,
      location = Some("Canada")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "CAN-DFLTMC",
      name = "Mountain Canada Default",
      `type` = ProviderType.Default,
      location = Some("Canada")
    )),
    ProviderDAO.upsertProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "CAN-DFLTPC",
      name = "Pacific Canada Default",
      `type` = ProviderType.Default,
      location = Some("Canada")
    ))
  )

  def makeIdxUnique = MigrationItem(56L, ProviderTable.table)(
    _.dropIndexes(
      _.old_sourceIdx
    ).addIndexes(
      _.sourceIdx
    )
  )()

}
