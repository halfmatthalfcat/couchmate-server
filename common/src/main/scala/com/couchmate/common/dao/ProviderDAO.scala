package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{Provider, ProviderType}
import com.couchmate.common.tables.ProviderTable
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object ProviderDAO {
  private[this] lazy val getProviderQuery = Compiled { (providerId: Rep[Long]) =>
    ProviderTable.table.filter(_.providerId === providerId)
  }

  def getProvider(providerId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Provider]] = cache(
    "getProvider",
    providerId
  )(db.run(getProviderQuery(providerId).result.headOption))()

  private[this] lazy val getProvidersForTypeQuery = Compiled { (`type`: Rep[ProviderType]) =>
    ProviderTable.table.filter(_.`type` === `type`)
  }

  def getProvidersForType(`type`: ProviderType)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[Provider]] = cache(
    "getProvidersForType",
    `type`.entryName
  )(db.run(getProvidersForTypeQuery(`type`).result))()

  private[this] lazy val getProviderForExtAndOwnerQuery = Compiled {
    (extId: Rep[String], providerOwnerId: Rep[Option[Long]]) =>
      ProviderTable.table.filter { p =>
        p.extId === extId &&
        p.providerOwnerId === providerOwnerId
      }
  }

  def getProviderForExtAndOwner(extId: String, providerOwnerId: Option[Long])(
    bust: Boolean = false
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Provider]] = cache(
    "getProviderForExtAndOwner"
  )(db.run(getProviderForExtAndOwnerQuery(
    extId,
    providerOwnerId
  ).result.headOption))(bust = bust)

  private[this] def addProviderForId(p: Provider)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addProviderForId",
    p.extId,
  )(db.run(
    sql"SELECT insert_or_get_provider_id(${p.providerOwnerId}, ${p.extId}, ${p.name}, ${p.`type`}, ${p.location}, ${p.device})"
      .as[Long].head
  ))()

  def addOrGetProvider(provider: Provider)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Provider] = cache(
    "addOrGetProvider",
    provider.extId
  )(for {
    exists <- getProviderForExtAndOwner(
      provider.extId,
      Some(provider.providerOwnerId)
    )()
    p <- exists.fold(for {
      _ <- addProviderForId(provider)
      selected <- getProviderForExtAndOwner(
        provider.extId,
        Some(provider.providerOwnerId)
      )(bust = true).map(_.get)
    } yield selected)(Future.successful)
  } yield p)()

}
