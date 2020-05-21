package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ProviderOwnerTable
import com.couchmate.data.models.ProviderOwner
import com.couchmate.external.gracenote.models.GracenoteProvider

import scala.concurrent.{ExecutionContext, Future}

class ProviderOwnerDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getProviderOwner(providerOwnerId: Long): Future[Option[ProviderOwner]] = {
    db.run(ProviderOwnerDAO.getProviderOwner(providerOwnerId).result.headOption)
  }

  def getProviderOwnerForName(name: String): Future[Option[ProviderOwner]] = {
    db.run(ProviderOwnerDAO.getProviderOwnerForName(name).result.headOption)
  }

  def getProviderOwnerForExt(extProviderOwnerId: String): Future[Option[ProviderOwner]] = {
    db.run(ProviderOwnerDAO.getProviderOwnerForExt(extProviderOwnerId).result.headOption)
  }

  def upsertProviderOwner(providerOwner: ProviderOwner): Future[ProviderOwner] = db.run(
    providerOwner.providerOwnerId.fold[DBIO[ProviderOwner]](
      (ProviderOwnerTable.table returning ProviderOwnerTable.table) += providerOwner
    ) { (providerOwnerId: Long) => for {
      _ <- ProviderOwnerTable.table.update(providerOwner)
      updated <- ProviderOwnerDAO.getProviderOwner(providerOwnerId).result.head
    } yield updated}.transactionally
  )

  def getProviderOwnerFromGracenote(
    gracenoteProvider: GracenoteProvider,
    country: Option[String],
  ): Future[ProviderOwner] = {
    if (gracenoteProvider.mso.isDefined) {
      db.run((for {
        exists <- ProviderOwnerDAO.getProviderOwnerForExt(gracenoteProvider.mso.get.id).result.headOption
        owner <- exists.fold[DBIO[ProviderOwner]](
          (ProviderOwnerTable.table returning ProviderOwnerTable.table) += ProviderOwner(
            providerOwnerId = None,
            extProviderOwnerId = Some(gracenoteProvider.mso.get.id),
            name = gracenoteProvider.getOwnerName,
          )
        )(DBIO.successful)
      } yield owner).transactionally)
    } else {
      db.run((for {
        exists <- ProviderOwnerDAO.getProviderOwnerForName(gracenoteProvider.getName(country)).result.headOption
        owner <- exists.fold[DBIO[ProviderOwner]](
          (ProviderOwnerTable.table returning ProviderOwnerTable.table) += ProviderOwner(
            providerOwnerId = None,
            extProviderOwnerId = None,
            name = gracenoteProvider.getName(country),
          )
        )(DBIO.successful)
      } yield owner).transactionally)
    }
  }
}

object ProviderOwnerDAO {
  private[dao] lazy val getProviderOwner = Compiled { (providerOwnerId: Rep[Long]) =>
    ProviderOwnerTable.table.filter(_.providerOwnerId === providerOwnerId)
  }

  private[dao] lazy val getProviderOwnerForName = Compiled { (name: Rep[String]) =>
    ProviderOwnerTable.table.filter(_.name === name)
  }

  private[dao] lazy val getProviderOwnerForExt = Compiled { (extProviderOwnerId: Rep[String]) =>
    ProviderOwnerTable.table.filter(_.extProviderOwnerId === extProviderOwnerId)
  }
}
