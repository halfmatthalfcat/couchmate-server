package com.couchmate.migration.migrations

import com.couchmate.common.dao.ProviderDAO
import com.couchmate.common.models.data.{Provider, ProviderType}
import com.couchmate.common.tables.ProviderTable
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.migration.db.MigrationItem
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object ProviderMigrations {

  def init = MigrationItem(3L, ProviderTable.table)(
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
  )()

  def makeIdxUnique = MigrationItem(56L, ProviderTable.table)(
    _.dropIndexes(
      _.old_sourceIdx
    ).addIndexes(
      _.sourceIdx
    )
  )()

  def addDevice(
    implicit
    ec: ExecutionContext,
    db: Database,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ) = MigrationItem(57L, ProviderTable.table)(
    _.addColumns(
      _.device
    )
  )(() => Seq(
    () => ProviderDAO.addOrGetProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "USA-DFLTE",
      name = "Eastern US Default",
      `type` = ProviderType.Default,
      location = Some("USA"),
      device = None
    )),
    () => ProviderDAO.addOrGetProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "USA-DFLTC",
      name = "Central US Default",
      `type` = ProviderType.Default,
      location = Some("USA"),
      device = None
    )),
    () => ProviderDAO.addOrGetProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "USA-DFLTM",
      name = "Mountain US Default",
      `type` = ProviderType.Default,
      location = Some("USA"),
      device = None
    )),
    () => ProviderDAO.addOrGetProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "USA-DFLTP",
      name = "Pacific US Default",
      `type` = ProviderType.Default,
      location = Some("USA"),
      device = None
    )),
    () => ProviderDAO.addOrGetProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "USA-DFLTH",
      name = "Hawaii US Default",
      `type` = ProviderType.Default,
      location = Some("USA"),
      device = None
    )),
    () => ProviderDAO.addOrGetProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "USA-DFLTA",
      name = "Alaska US Default",
      `type` = ProviderType.Default,
      location = Some("USA"),
      device = None
    )),
    () => ProviderDAO.addOrGetProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "CAN-DFLTEC",
      name = "Eastern Canada Default",
      `type` = ProviderType.Default,
      location = Some("Canada"),
      device = None
    )),
    () => ProviderDAO.addOrGetProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "CAN-DFLTCC",
      name = "Central Canada Default",
      `type` = ProviderType.Default,
      location = Some("Canada"),
      device = None
    )),
    () => ProviderDAO.addOrGetProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "CAN-DFLTMC",
      name = "Mountain Canada Default",
      `type` = ProviderType.Default,
      location = Some("Canada"),
      device = None
    )),
    () => ProviderDAO.addOrGetProvider(Provider(
      providerId = None,
      providerOwnerId = 1L,
      extId = "CAN-DFLTPC",
      name = "Pacific Canada Default",
      `type` = ProviderType.Default,
      location = Some("Canada"),
      device = None
    ))
  ).foldLeft(Future.successful(Seq.empty[Provider]))((acc, curr) => for {
    results <- acc
    result <- curr()
    _ = System.out.println(s"Created ${result}")
  } yield results :+ result))

}
