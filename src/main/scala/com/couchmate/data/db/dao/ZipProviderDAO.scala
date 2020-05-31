package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.{Flow, Source}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{ProviderTable, ZipProviderTable}
import com.couchmate.data.models.{CountryCode, Provider, ZipProvider, ZipProviderDetailed}

import scala.concurrent.{ExecutionContext, Future}

trait ZipProviderDAO {

  def getZipProviders(
    implicit
    db: Database
  ): Future[Seq[ZipProvider]] =
    db.run(ZipProviderTable.table.result)

  def getZipProviders$(
    implicit
    session: SlickSession
  ): Source[ZipProvider, NotUsed] =
    Slick.source(ZipProviderTable.table.result)

  def zipProvidersExistForZipAndCode(
    zipCode: String,
    countryCode: CountryCode
  )(
    implicit
    db: Database
  ): Future[Boolean] =
    db.run(ZipProviderDAO.zipProvidersExistForZipAndCode(zipCode, countryCode))

  def zipProvidersExistForZipAndCode$()(
    implicit
    session: SlickSession
  ): Flow[(String, CountryCode), Boolean, NotUsed] =
    Slick.flowWithPassThrough(
      (ZipProviderDAO.zipProvidersExistForZipAndCode _).tupled
    )

  def getZipProviderForZipAndCode(
    zipCode: String,
    countryCode: CountryCode,
    providerId: Long
  )(
    implicit
    db: Database
  ): Future[Option[ZipProvider]] =
    db.run(ZipProviderDAO.getZipProviderForZipAndCode(zipCode, countryCode, providerId))

  def getProviderForZipAndCode$()(
    implicit
    session: SlickSession
  ): Flow[(String, CountryCode, Long), Option[ZipProvider], NotUsed] =
    Slick.flowWithPassThrough(
      (ZipProviderDAO.getZipProviderForZipAndCode _).tupled
    )

  def getZipProvidersForZipAndCode(
    zipCode: String,
    countryCode: CountryCode
  )(
    implicit
    db: Database
  ): Future[Seq[ZipProvider]] =
    db.run(ZipProviderDAO.getZipProvidersForZipAndCode(zipCode, countryCode))

  def getZipProvidersByZip$()(
    implicit
    session: SlickSession
  ): Flow[(String, CountryCode), Seq[ZipProvider], NotUsed] =
    Slick.flowWithPassThrough(
      (ZipProviderDAO.getZipProvidersForZipAndCode _).tupled
    )

  def getProvidersForZipAndCode(
    zipCode: String,
    countryCode: CountryCode
  )(
    implicit
    db: Database
  ): Future[Seq[Provider]] =
    db.run(ZipProviderDAO.getProvidersForZipAndCode(zipCode, countryCode))

  def getProvidersForZipAndCode$()(
    implicit
    session: SlickSession
  ): Flow[(String, CountryCode), Seq[Provider], NotUsed] =
    Slick.flowWithPassThrough(
      (ZipProviderDAO.getProvidersForZipAndCode _).tupled
    )

  def getZipMap(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[ZipProviderDetailed]] =
    db.run(ZipProviderDAO.getZipMap).map(_.map((ZipProviderDetailed.apply _).tupled))

  def addZipProvider(zipProvider: ZipProvider)(
    implicit
    db: Database
  ): Future[ZipProvider] =
    db.run(ZipProviderDAO.addZipProvider(zipProvider))

  def addZipProvider$()(
    implicit
    session: SlickSession
  ): Flow[ZipProvider, ZipProvider, NotUsed] =
    Slick.flowWithPassThrough(ZipProviderDAO.addZipProvider)
}

object ZipProviderDAO {
  private[this] lazy val zipProvidersExistForZipAndCodeQuery = Compiled { (zipCode: Rep[String], countryCode: Rep[CountryCode]) =>
    ZipProviderTable.table.filter { zp =>
      zp.zipCode === zipCode &&
      zp.countryCode === countryCode
    }.exists
  }

  private[dao] def zipProvidersExistForZipAndCode(zipCode: String, countryCode: CountryCode): DBIO[Boolean] =
    zipProvidersExistForZipAndCodeQuery(zipCode, countryCode).result

  private[this] lazy val getZipProviderForZipAndCodeQuery = Compiled {
    (zipCode: Rep[String], countryCode: Rep[CountryCode], providerId: Rep[Long]) =>
      ZipProviderTable.table.filter { zp =>
        zp.zipCode === zipCode &&
        zp.countryCode === countryCode &&
        zp.providerId === providerId
      }
  }

  private[dao] def getZipProviderForZipAndCode(zipCode: String, countryCode: CountryCode, providerId: Long): DBIO[Option[ZipProvider]] =
    getZipProviderForZipAndCodeQuery(zipCode, countryCode, providerId).result.headOption

  private[this] lazy val getProvidersForZipAndCodeQuery = Compiled { (zipCode: Rep[String], countryCode: Rep[CountryCode]) =>
    for {
      zp <- ZipProviderTable.table if (
        zp.zipCode === zipCode &&
        zp.countryCode === countryCode
      )
      p <- ProviderTable.table if p.providerId === zp.providerId
    } yield p
  }

  private[dao] def getProvidersForZipAndCode(zipCode: String, countryCode: CountryCode): DBIO[Seq[Provider]] =
    getProvidersForZipAndCodeQuery(zipCode, countryCode).result

  private[this] lazy val getZipProvidersForZipAndCodeQuery = Compiled {
    (zipCode: Rep[String], countryCode: Rep[CountryCode]) =>
      ZipProviderTable.table.filter { zp =>
        zp.zipCode === zipCode &&
        zp.countryCode === countryCode
      }
  }

  private[dao] def getZipProvidersForZipAndCode(
    zipCode: String,
    countryCode: CountryCode
  ): DBIO[Seq[ZipProvider]] =
    getZipProvidersForZipAndCodeQuery(zipCode, countryCode).result

  private[this] lazy val getZipMapQuery = Compiled {
    for {
      zp <- ZipProviderTable.table
      p <- ProviderTable.table if p.providerId === zp.providerId
    } yield (
      zp.zipCode,
      zp.countryCode,
      zp.providerId,
      p.name,
      p.`type`,
      p.location
    )
  }

  private[dao] def getZipMap: DBIO[Seq[(String, CountryCode, Long, String, String, Option[String])]] =
    getZipMapQuery.result

  private[dao] def addZipProvider(zipProvider: ZipProvider): DBIO[ZipProvider] =
    (ZipProviderTable.table returning ZipProviderTable.table) += zipProvider
}
