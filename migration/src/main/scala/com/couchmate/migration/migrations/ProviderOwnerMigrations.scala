package com.couchmate.migration.migrations

import com.couchmate.common.dao.ProviderOwnerDAO
import com.couchmate.common.models.data.ProviderOwner
import com.couchmate.common.tables.ProviderOwnerTable
import com.couchmate.migration.db.MigrationItem
import com.couchmate.common.db.PgProfile.api._
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object ProviderOwnerMigrations {

  def init(
    implicit
    ec: ExecutionContext,
    db: Database,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ) = MigrationItem(2L, ProviderOwnerTable.table)(
    _.create.addColumns(
      _.providerOwnerId,
      _.extProviderOwnerId,
      _.name,
    )
  )(() => Future.sequence(Seq(
    ProviderOwnerDAO.insertProviderOwner(
      extProviderOwnerId = "x",
      name = "Default"
    )
  )))

  def addIdx = MigrationItem(55L, ProviderOwnerTable.table)(
    _.addIndexes(_.idx)
  )()

}
