package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{ProviderTable, ZipProviderTable}
import com.couchmate.data.models.{Provider, ZipProvider}

import scala.concurrent.{ExecutionContext, Future}

trait ZipProviderDAO {

  def getZipProvidersByZip(zipCode: String)(
    implicit
    db: Database
  ): Future[Seq[ZipProvider]] = {
    db.run(ZipProviderDAO.getZipProvidersForZip(zipCode).result)
  }

  def getProvidersForZip(zipCode: String)(
    implicit
    db: Database
  ): Future[Seq[Provider]] = {
    db.run(ZipProviderDAO.getProvidersForZip(zipCode).result)
  }

  def getProviderForProviderAndZip(providerId: Long, zipCode: String)(
    implicit
    db: Database
  ): Future[Option[ZipProvider]] = {
    db.run(ZipProviderDAO.getProviderForProviderAndZip(providerId, zipCode).result.headOption)
  }

  def addZipProvider(zipProvider: ZipProvider)(
    implicit
    db: Database
  ): Future[ZipProvider] = {
    db.run((ZipProviderTable.table returning ZipProviderTable.table) += zipProvider)
  }

  def getZipProviderFromGracenote(zipCode: String, provider: Provider)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[ZipProvider] = {
    db.run((for {
      exists <- ZipProviderDAO.getProviderForProviderAndZip(
        provider.providerId.get,
        zipCode,
      ).result.headOption
      zipProvider <- exists.fold[DBIO[ZipProvider]](
        (ZipProviderTable.table returning ZipProviderTable.table) += ZipProvider(
          providerId = provider.providerId.get,
          zipCode = zipCode,
        )
      )(DBIO.successful)
    } yield zipProvider).transactionally)
  }

}

object ZipProviderDAO {
  private[dao] lazy val getZipProvidersForZip = Compiled { (zipCode: Rep[String]) =>
    ZipProviderTable.table.filter(_.zipCode === zipCode)
  }

  private[dao] lazy val getProvidersForZip = Compiled { (zipCode: Rep[String]) =>
    for {
      zp <- ZipProviderTable.table if zp.zipCode === zipCode
      p <- ProviderTable.table if p.providerId === zp.providerId
    } yield p
  }

  private[dao] lazy val getProviderForProviderAndZip = Compiled {
    (providerId: Rep[Long], zipCode: Rep[String]) =>
      ZipProviderTable.table.filter { zp =>
        zp.providerId === providerId &&
        zp.zipCode === zipCode
      }
  }
}
