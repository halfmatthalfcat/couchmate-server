package com.couchmate.data.db.dao

import com.couchmate.common.models.ZipProvider
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.ZipProviderQueries
import com.couchmate.data.db.table.ZipProviderTable
import com.couchmate.data.models.{Provider, ZipProvider}

import scala.concurrent.{ExecutionContext, Future}

class ZipProviderDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends ZipProviderQueries {

  def getZipProvidersByZip(zipCode: String): Future[Seq[ZipProvider]] = {
    db.run(super.getZipProvidersForZip(zipCode).result)
  }

  def getProvidersForZip(zipCode: String): Future[Seq[Provider]] = {
    db.run(super.getProvidersForZip(zipCode).result)
  }

  def providerExistsForProviderAndZip(providerId: Long, zipCode: String): Future[Boolean] = {
    db.run(super.providerExistsForProviderAndZip(providerId, zipCode).result)
  }

  def addZipProvider(zipProvider: ZipProvider): Future[ZipProvider] = {
    db.run((ZipProviderTable.table returning ZipProviderTable.table) += zipProvider)
  }

}
