package com.couchmate.common.dao

import java.util.UUID
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{Provider, UserProvider}
import com.couchmate.common.tables.{ProviderTable, UserProviderTable}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object UserProviderDAO {
  private[this] lazy val getUserProviderQuery = Compiled { (userId: Rep[UUID]) =>
    UserProviderTable.table.filter(_.userId === userId)
  }

  def getUserProvider(userId: UUID)(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[UserProvider]] = cache(
    "getUserProvider",
    userId.toString
  )(db.run(getUserProviderQuery(userId).result.headOption))(
    bust = bust
  )

  private[this] lazy val getProvidersQuery = Compiled { (userId: Rep[UUID]) =>
    for {
      p <- ProviderTable.table
      up <- UserProviderTable.table if ( up.userId === userId )
    } yield p
  }

  def getProviders(userId: UUID)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[Provider]] = cache(
    "getProviders",
    userId.toString
  )(db.run(getProvidersQuery(userId).result))()

  private[this] lazy val userProviderExistsQuery = Compiled {
    (providerId: Rep[Long]) =>
      UserProviderTable.table.filter { up =>
        up.providerId === providerId
      }.exists
  }

  def userProviderExists(providerId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Boolean] = cache(
    "userProviderExists",
    providerId
  )(db.run(userProviderExistsQuery(providerId).result))()

  private[this] lazy val getUniqueInternalProvidersQuery = Compiled {
    UserProviderTable.table.distinct
  }

  def getUniqueInternalProviders()(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[UserProvider]] = cache(
    "getUniqueInternalProviders"
  )(db.run(getUniqueInternalProvidersQuery.result))()

  private[this] lazy val getUniqueProvidersQuery = Compiled {
    (for {
      p <- ProviderTable.table
      up <- UserProviderTable.table if ( p.providerId === up.providerId )
    } yield p).distinct
  }

  def getUniqueProviders()(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[Provider]] = cache(
    "getUniqueProviders"
  )(db.run(getUniqueProvidersQuery.result))()

  def addUserProvider(userProvider: UserProvider)(
    implicit
    db: Database
  ): Future[UserProvider] = db.run(
    (UserProviderTable.table returning UserProviderTable.table) += userProvider
  )


  def updateUserProvider(userProvider: UserProvider)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[UserProvider] = for {
    _ <- db.run(
      UserProviderTable.table.filter(_.userId === userProvider.userId).update(userProvider)
    )
    provider <- getUserProvider(
      userProvider.userId
    )(bust = true).map(_.get)
  } yield provider
}
