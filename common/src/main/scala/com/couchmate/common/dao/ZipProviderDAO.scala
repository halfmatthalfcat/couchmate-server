package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{Provider, ProviderType, ZipProvider, ZipProviderDetailed}
import com.couchmate.common.tables.{ProviderTable, ZipProviderTable}
import com.neovisionaries.i18n.CountryCode
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object ZipProviderDAO {
  def getZipProviders(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[ZipProvider]] = cache(
    "getZipProviders"
  )(db.run(ZipProviderTable.table.result))()

  private[this] lazy val zipProvidersExistForZipAndCodeQuery = Compiled { (zipCode: Rep[String], countryCode: Rep[CountryCode]) =>
    ZipProviderTable.table.filter { zp =>
      zp.zipCode === zipCode &&
      zp.countryCode === countryCode
    }.exists
  }

  def zipProvidersExistForZipAndCode(
    zipCode: String,
    countryCode: CountryCode
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Boolean] = cache(
    "zipProvidersExistForZipAndCode",
    zipCode,
    countryCode.getAlpha3
  )(db.run(zipProvidersExistForZipAndCodeQuery(zipCode, countryCode).result))()

  private[this] lazy val getZipProviderForZipAndCodeQuery = Compiled {
    (zipCode: Rep[String], countryCode: Rep[CountryCode], providerId: Rep[Long]) =>
      ZipProviderTable.table.filter { zp =>
        zp.zipCode === zipCode &&
        zp.countryCode === countryCode &&
        zp.providerId === providerId
      }
  }

  def getZipProviderForZipAndCode(
    zipCode: String,
    countryCode: CountryCode,
    providerId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[ZipProvider]] = cache(
    "getZipProviderForZipAndCode",
    zipCode,
    countryCode.getAlpha3,
    providerId
  )(db.run(getZipProviderForZipAndCodeQuery(
    zipCode,
    countryCode,
    providerId
  ).result.headOption))()

  private[this] lazy val getProvidersForZipAndCodeQuery = Compiled { (zipCode: Rep[String], countryCode: Rep[CountryCode]) =>
    for {
      zp <- ZipProviderTable.table if (
        zp.zipCode === zipCode &&
        zp.countryCode === countryCode
      )
      p <- ProviderTable.table if p.providerId === zp.providerId
    } yield p
  }

  def getProvidersForZipAndCode(
    zipCode: String,
    countryCode: CountryCode
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[Provider]] = cache(
    "getProvidersForZipAndCode",
    zipCode,
    countryCode.getAlpha3
  )(db.run(getProvidersForZipAndCodeQuery(zipCode, countryCode).result))()


  def getZipProvidersForZipAndCode(
    zipCode: String,
    countryCode: CountryCode
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[ZipProvider]] = cache(
    "getZipProvidersForZipAndCode",
    zipCode,
    countryCode.getAlpha3
  )(db.run(getZipProvidersForZipAndCodeQuery(zipCode, countryCode).result))()

  private[this] lazy val getZipProvidersForZipAndCodeQuery = Compiled {
    (zipCode: Rep[String], countryCode: Rep[CountryCode]) =>
      ZipProviderTable.table.filter { zp =>
        zp.zipCode === zipCode &&
        zp.countryCode === countryCode
      }
  }

  private[this] lazy val getZipMapQuery = Compiled {
    for {
      zp <- ZipProviderTable.table
      p <- ProviderTable.table if p.providerId === zp.providerId
    } yield (
      zp.zipCode,
      zp.countryCode,
      zp.providerId,
      p.providerOwnerId,
      p.extId,
      p.name,
      p.device,
      p.`type`,
      p.location
    )
  }

  def getZipMap(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Seq[ZipProviderDetailed]] =
    db.run(getZipMapQuery.result).map(_.map((ZipProviderDetailed.apply _).tupled))

  def addZipProvider(zipProvider: ZipProvider)(
    implicit
    db: Database
  ): Future[ZipProvider] =
    db.run((ZipProviderTable.table returning ZipProviderTable.table) += zipProvider)
}
