package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.ProviderQueries
import com.couchmate.data.db.table.ProviderTable
import com.couchmate.data.models.Provider

import scala.concurrent.{ExecutionContext, Future}

class ProviderDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends ProviderQueries {

  def getProvider(providerId: Long): Future[Option[Provider]] = {
    db.run(super.getProvider(providerId).result.headOption)
  }

  def getProviderForExtAndOwner(extId: String, providerOwnerId: Option[Long]): Future[Option[Provider]] = {
    db.run(super.getProviderForExtAndOwner(extId, providerOwnerId).result.headOption)
  }

  def upsertProvider(provider: Provider): Future[Provider] =
    provider.providerId.fold(
      db.run((ProviderTable.table returning ProviderTable.table) += provider)
    ) { (providerId: Long) => db.run(for {
      _ <- ProviderTable.table.update(provider)
      updated <- super.getProvider(providerId)
    } yield updated.result.head.transactionally)}

}
