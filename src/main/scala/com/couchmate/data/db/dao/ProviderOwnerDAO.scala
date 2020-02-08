package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.ProviderOwnerQueries
import com.couchmate.data.db.table.ProviderOwnerTable
import com.couchmate.data.models.ProviderOwner

import scala.concurrent.{ExecutionContext, Future}

class ProviderOwnerDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends ProviderOwnerQueries {

  def getProviderOwner(providerOwnerId: Long): Future[Option[ProviderOwner]] = {
    db.run(super.getProviderOwner(providerOwnerId).result.headOption)
  }

  def getProviderOwnerForName(name: String): Future[Option[ProviderOwner]] = {
    db.run(super.getProviderOwnerForName(name).result.headOption)
  }

  def getProviderOwnerForExt(extProviderOwnerId: String): Future[Option[ProviderOwner]] = {
    db.run(super.getProviderOwnerForExt(extProviderOwnerId).result.headOption)
  }

  def upsertProviderOwner(providerOwner: ProviderOwner): Future[ProviderOwner] =
    providerOwner.providerOwnerId.fold(
      db.run((ProviderOwnerTable.table returning ProviderOwnerTable.table) += providerOwner)
    ) { (providerOwnerId: Long) => db.run(for {
      _ <- ProviderOwnerTable.table.update(providerOwner)
      updated <- super.getProviderOwner(providerOwnerId)
    } yield updated.result.head.transactionally)}

}
