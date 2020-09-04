package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.Provider
import com.couchmate.common.tables.ProviderTable

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

  def getProvidersForType(`type`: String)(
    implicit
    db: Database
  ): Future[Seq[Provider]] =
    db.run(ProviderDAO.getProvidersForType(`type`))

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
}

object ProviderDAO {
  private[this] lazy val getProviderQuery = Compiled { (providerId: Rep[Long]) =>
    ProviderTable.table.filter(_.providerId === providerId)
  }

  private[common] def getProvider(providerId: Long): DBIO[Option[Provider]] =
    getProviderQuery(providerId).result.headOption

  private[this] lazy val getProvidersForTypeQuery = Compiled { (`type`: Rep[String]) =>
    ProviderTable.table.filter(_.`type` === `type`)
  }

  private[common] def getProvidersForType(`type`: String): DBIO[Seq[Provider]] =
    getProvidersForTypeQuery(`type`).result

  private[this] lazy val getProviderForExtAndOwnerQuery = Compiled {
    (extId: Rep[String], providerOwnerId: Rep[Option[Long]]) =>
      ProviderTable.table.filter { p =>
        p.extId === extId &&
        p.providerOwnerId === providerOwnerId
      }
  }

  private[common] def getProviderForExtAndOwner(
    extId: String,
    providerOwnerId: Option[Long]
  ): DBIO[Option[Provider]] =
    getProviderForExtAndOwnerQuery(extId, providerOwnerId).result.headOption

  private[common] def upsertProvider(provider: Provider)(
    implicit
    ec: ExecutionContext
  ): DBIO[Provider] =
    provider.providerId.fold[DBIO[Provider]](
      (ProviderTable.table returning ProviderTable.table) += provider
    ) { (providerId: Long) => for {
      _ <- ProviderTable
        .table
        .filter(_.providerId === providerId)
        .update(provider)
      updated <- ProviderDAO.getProvider(providerId)
    } yield updated.get}
}
