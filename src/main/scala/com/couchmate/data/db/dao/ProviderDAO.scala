package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ProviderTable
import com.couchmate.data.models.{Provider, ProviderOwner}
import com.couchmate.data.thirdparty.gracenote.GracenoteProvider

import scala.concurrent.{ExecutionContext, Future}

class ProviderDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getProvider(providerId: Long): Future[Option[Provider]] = {
    db.run(ProviderDAO.getProvider(providerId).result.headOption)
  }

  def getProviderForExtAndOwner(extId: String, providerOwnerId: Option[Long]): Future[Option[Provider]] = {
    db.run(ProviderDAO.getProviderForExtAndOwner(extId, providerOwnerId).result.headOption)
  }

  def upsertProvider(provider: Provider): Future[Provider] = db.run(
    provider.providerId.fold[DBIO[Provider]](
      (ProviderTable.table returning ProviderTable.table) += provider
    ) { (providerId: Long) => for {
      _ <- ProviderTable.table.update(provider)
      updated <- ProviderDAO.getProvider(providerId).result.head
    } yield updated}.transactionally
  )

  def getProviderFromGracenote(
    provider: GracenoteProvider,
    owner: ProviderOwner,
    country: Option[String],
  ): Future[Provider] = db.run((for {
    exists <- ProviderDAO.getProviderForExtAndOwner(
      provider.lineupId,
      owner.providerOwnerId,
    ).result.headOption
    provider <- exists.fold[DBIO[Provider]](
      (ProviderTable.table returning ProviderTable.table) += Provider(
        providerId = None,
        providerOwnerId = owner.providerOwnerId,
        extId = provider.lineupId,
        name = provider.getName(country),
        location = provider.location,
        `type` = provider.`type`,
      )
    )(DBIO.successful)
  } yield provider).transactionally)

}

object ProviderDAO {
  private[dao] lazy val getProvider = Compiled { (providerId: Rep[Long]) =>
    ProviderTable.table.filter(_.providerId === providerId)
  }

  private[dao] lazy val getProviderForExtAndOwner = Compiled {
    (extId: Rep[String], providerOwnerId: Rep[Option[Long]]) =>
      ProviderTable.table.filter { p =>
        p.extId === extId &&
        p.providerOwnerId === providerOwnerId
      }
  }
}
