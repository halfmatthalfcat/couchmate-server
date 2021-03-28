package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.ProviderOwner
import com.couchmate.common.tables.ProviderOwnerTable
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object ProviderOwnerDAO {
  private[this] lazy val getProviderOwnerQuery = Compiled { (providerOwnerId: Rep[Long]) =>
    ProviderOwnerTable.table.filter(_.providerOwnerId === providerOwnerId)
  }

  def getProviderOwner(providerOwnerId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[ProviderOwner]] = cache(
    "getProviderOwner",
    providerOwnerId
  )(db.run(getProviderOwnerQuery(providerOwnerId).result.headOption))()

  private[this] lazy val getProviderOwnerForNameQuery = Compiled { (name: Rep[String]) =>
    ProviderOwnerTable.table.filter(_.name === name)
  }

  def getProviderOwnerForName(name: String)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[ProviderOwner]] = cache(
    "getProviderOwnerForName",
    name
  )(db.run(getProviderOwnerForNameQuery(name).result.headOption))()

  private[this] lazy val getProviderOwnerForExtQuery = Compiled { (extProviderOwnerId: Rep[String]) =>
    ProviderOwnerTable.table.filter(_.extProviderOwnerId === extProviderOwnerId)
  }

  def getProviderOwnerForExt(extProviderOwnerId: String)(
    bust: Boolean = false
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[ProviderOwner]] = cache(
    "getProviderOwnerForExt",
    extProviderOwnerId
  )(db.run(getProviderOwnerForExtQuery(extProviderOwnerId).result.headOption))(
    bust = bust
  )

  private[this] def addProviderOwnerForId(
    extProviderOwnerId: String,
    name: String
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addProviderOwnerForId",
    extProviderOwnerId,
    name
  )(db.run(
    sql"SELECT insert_or_get_provider_owner_id($extProviderOwnerId, $name)"
      .as[Long].head
  ))()

  def addOrGetProviderOwner(
    extProviderOwnerId: String,
    name: String
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[ProviderOwner] = cache(
    "addOrGetProviderOwner",
    extProviderOwnerId,
    name
  )(for {
    exists <- getProviderOwnerForExt(extProviderOwnerId)()
    po <- exists.fold(for {
      _ <- addProviderOwnerForId(extProviderOwnerId, name)
      selected <- getProviderOwnerForExt(extProviderOwnerId)(bust = true)
        .map(_.get)
    } yield selected)(Future.successful)
  } yield po)()

}
