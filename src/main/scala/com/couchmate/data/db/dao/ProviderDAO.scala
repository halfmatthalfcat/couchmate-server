package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ProviderTable
import com.couchmate.data.models.{Provider, ProviderOwner}
import com.couchmate.external.gracenote.models.GracenoteProvider

import scala.concurrent.{ExecutionContext, Future}

trait ProviderDAO {

  def getProvider(providerId: Long)(
    implicit
    db: Database
  ): Future[Option[Provider]] =
    db.run(ProviderDAO.getProvider(providerId))

  def getProvider$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Provider], NotUsed] =
    Slick.flowWithPassThrough(ProviderDAO.getProvider)

  def getProviderForExtAndOwner(extId: String, providerOwnerId: Option[Long])(
    implicit
    db: Database
  ): Future[Option[Provider]] =
    db.run(ProviderDAO.getProviderForExtAndOwner(extId, providerOwnerId))

  def getProviderForExtAndOwner$()(
    implicit
    session: SlickSession
  ): Flow[(String, Option[Long]), Option[Provider], NotUsed] =
    Slick.flowWithPassThrough(
      (ProviderDAO.getProviderForExtAndOwner _).tupled
    )

  def upsertProvider(provider: Provider)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Provider] =
    db.run(ProviderDAO.upsertProvider(provider))

  def upsertProvider$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[Provider, Provider, NotUsed] =
    Slick.flowWithPassThrough(ProviderDAO.upsertProvider)

  def getProviderFromGracenote(
    provider: GracenoteProvider,
    owner: ProviderOwner,
    country: Option[String],
  )(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Provider] =
    db.run(ProviderDAO.getProviderFromGracenote(
      provider,
      owner,
      country
    ))

  def getProviderFromGracenote$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[(GracenoteProvider, ProviderOwner, Option[String]), Provider, NotUsed] =
    Slick.flowWithPassThrough(
      (ProviderDAO.getProviderFromGracenote _).tupled
    )

}

object ProviderDAO {
  private[this] lazy val getProviderQuery = Compiled { (providerId: Rep[Long]) =>
    ProviderTable.table.filter(_.providerId === providerId)
  }

  private[dao] def getProvider(providerId: Long): DBIO[Option[Provider]] =
    getProviderQuery(providerId).result.headOption

  private[this] lazy val getProviderForExtAndOwnerQuery = Compiled {
    (extId: Rep[String], providerOwnerId: Rep[Option[Long]]) =>
      ProviderTable.table.filter { p =>
        p.extId === extId &&
        p.providerOwnerId === providerOwnerId
      }
  }

  private[dao] def getProviderForExtAndOwner(
    extId: String,
    providerOwnerId: Option[Long]
  ): DBIO[Option[Provider]] =
    getProviderForExtAndOwnerQuery(extId, providerOwnerId).result.headOption

  private[dao] def upsertProvider(provider: Provider)(
    implicit
    ec: ExecutionContext
  ): DBIO[Provider] =
    provider.providerId.fold[DBIO[Provider]](
      (ProviderTable.table returning ProviderTable.table) += provider
    ) { (providerId: Long) => for {
      _ <- ProviderTable.table.update(provider)
      updated <- ProviderDAO.getProvider(providerId)
    } yield updated.get}

  private[dao] def getProviderFromGracenote(
    provider: GracenoteProvider,
    owner: ProviderOwner,
    country: Option[String],
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Provider] = for {
    exists <- ProviderDAO.getProviderForExtAndOwner(
      provider.lineupId,
      owner.providerOwnerId,
    )
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
  } yield provider
}
