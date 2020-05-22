package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{ProviderTable, ZipProviderTable}
import com.couchmate.data.models.{Provider, ZipProvider}

import scala.concurrent.{ExecutionContext, Future}

trait ZipProviderDAO {

  def getZipProvidersByZip(zipCode: String)(
    implicit
    db: Database
  ): Future[Seq[ZipProvider]] =
    db.run(ZipProviderDAO.getZipProvidersForZip(zipCode))

  def getZipProvidersByZip$()(
    implicit
    session: SlickSession
  ): Flow[String, Seq[ZipProvider], NotUsed] =
    Slick.flowWithPassThrough(ZipProviderDAO.getZipProvidersForZip)

  def getProvidersForZip(zipCode: String)(
    implicit
    db: Database
  ): Future[Seq[Provider]] =
    db.run(ZipProviderDAO.getProvidersForZip(zipCode))

  def getProvidersForZip$()(
    implicit
    session: SlickSession
  ): Flow[String, Seq[Provider], NotUsed] =
    Slick.flowWithPassThrough(ZipProviderDAO.getProvidersForZip)

  def getProviderForProviderAndZip(providerId: Long, zipCode: String)(
    implicit
    db: Database
  ): Future[Option[ZipProvider]] =
    db.run(ZipProviderDAO.getProviderForProviderAndZip(providerId, zipCode))

  def getProviderForProviderAndZip$()(
    implicit
    session: SlickSession
  ): Flow[(Long, String), Option[ZipProvider], NotUsed] =
    Slick.flowWithPassThrough(
      (ZipProviderDAO.getProviderForProviderAndZip _).tupled
    )

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

  def getZipProviderFromGracenote(zipCode: String, provider: Provider)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[ZipProvider] =
    db.run(ZipProviderDAO.getZipProviderFromGracenote(zipCode, provider))

  def getZipProviderFromGracenote$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[(String, Provider), ZipProvider, NotUsed] =
    Slick.flowWithPassThrough(
      (ZipProviderDAO.getZipProviderFromGracenote _).tupled
    )

}

object ZipProviderDAO {
  private[this] lazy val getZipProvidersForZipQuery = Compiled { (zipCode: Rep[String]) =>
    ZipProviderTable.table.filter(_.zipCode === zipCode)
  }

  private[dao] def getZipProvidersForZip(zipCode: String): DBIO[Seq[ZipProvider]] =
    getZipProvidersForZipQuery(zipCode).result

  private[this] lazy val getProvidersForZipQuery = Compiled { (zipCode: Rep[String]) =>
    for {
      zp <- ZipProviderTable.table if zp.zipCode === zipCode
      p <- ProviderTable.table if p.providerId === zp.providerId
    } yield p
  }

  private[dao] def getProvidersForZip(zipCode: String): DBIO[Seq[Provider]] =
    getProvidersForZipQuery(zipCode).result

  private[this] lazy val getProviderForProviderAndZipQuery = Compiled {
    (providerId: Rep[Long], zipCode: Rep[String]) =>
      ZipProviderTable.table.filter { zp =>
        zp.providerId === providerId &&
        zp.zipCode === zipCode
      }
  }

  private[dao] def getProviderForProviderAndZip(
    providerId: Long,
    zipCode: String,
  ): DBIO[Option[ZipProvider]] =
    getProviderForProviderAndZipQuery(providerId, zipCode).result.headOption

  private[dao] def addZipProvider(zipProvider: ZipProvider): DBIO[ZipProvider] =
    (ZipProviderTable.table returning ZipProviderTable.table) += zipProvider

  private[dao] def getZipProviderFromGracenote(zipCode: String, provider: Provider)(
    implicit
    ec: ExecutionContext
  ): DBIO[ZipProvider] =
    for {
      exists <- ZipProviderDAO.getProviderForProviderAndZip(
        provider.providerId.get,
        zipCode,
      )
      zipProvider <- exists.fold[DBIO[ZipProvider]](
        (ZipProviderTable.table returning ZipProviderTable.table) += ZipProvider(
          providerId = provider.providerId.get,
          zipCode = zipCode,
        )
      )(DBIO.successful)
    } yield zipProvider
}
