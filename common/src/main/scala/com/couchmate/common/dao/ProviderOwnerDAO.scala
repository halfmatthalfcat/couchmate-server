package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.ProviderOwner
import com.couchmate.common.tables.ProviderOwnerTable

import scala.concurrent.{ExecutionContext, Future}

trait ProviderOwnerDAO {

  def getProviderOwner(providerOwnerId: Long)(
    implicit
    db: Database
  ): Future[Option[ProviderOwner]] =
    db.run(ProviderOwnerDAO.getProviderOwner(providerOwnerId))

  def getProviderOwner$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[ProviderOwner], NotUsed] =
    Slick.flowWithPassThrough(ProviderOwnerDAO.getProviderOwner)

  def getProviderOwnerForName(name: String)(
    implicit
    db: Database
  ): Future[Option[ProviderOwner]] =
    db.run(ProviderOwnerDAO.getProviderOwnerForName(name))

  def getProviderOwnerForName$()(
    implicit
    session: SlickSession
  ): Flow[String, Option[ProviderOwner], NotUsed] =
    Slick.flowWithPassThrough(ProviderOwnerDAO.getProviderOwnerForName)

  def getProviderOwnerForExt(extProviderOwnerId: String)(
    implicit
    db: Database
  ): Future[Option[ProviderOwner]] =
    db.run(ProviderOwnerDAO.getProviderOwnerForExt(extProviderOwnerId))

  def getProviderOwnerForExt$()(
    implicit
    session: SlickSession
  ): Flow[String, Option[ProviderOwner], NotUsed] =
    Slick.flowWithPassThrough(ProviderOwnerDAO.getProviderOwnerForExt)

  def upsertProviderOwner(providerOwner: ProviderOwner)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[ProviderOwner] =
    db.run(ProviderOwnerDAO.upsertProviderOwner(providerOwner))

  def upsertProviderOwner$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[ProviderOwner, ProviderOwner, NotUsed] =
    Slick.flowWithPassThrough(ProviderOwnerDAO.upsertProviderOwner)

  def getOrAddProviderOwner(providerOwner: ProviderOwner)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[ProviderOwner] =
    db.run(ProviderOwnerDAO.getOrAddProviderOwner(providerOwner))

  def getOrAddProviderOwner$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[ProviderOwner, ProviderOwner, NotUsed] =
    Slick.flowWithPassThrough(ProviderOwnerDAO.getOrAddProviderOwner)

  def addAndGetProviderOwner(
    extProviderOwnerId: String,
    name: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[ProviderOwner] =
    db.run(ProviderOwnerDAO.addAndGetProviderOwner(
      extProviderOwnerId, name
    ))
}

object ProviderOwnerDAO {
  private[this] lazy val getProviderOwnerQuery = Compiled { (providerOwnerId: Rep[Long]) =>
    ProviderOwnerTable.table.filter(_.providerOwnerId === providerOwnerId)
  }

  private[common] def getProviderOwner(providerOwnerId: Long): DBIO[Option[ProviderOwner]] =
    getProviderOwnerQuery(providerOwnerId).result.headOption

  private[this] lazy val getProviderOwnerForNameQuery = Compiled { (name: Rep[String]) =>
    ProviderOwnerTable.table.filter(_.name === name)
  }

  private[common] def getProviderOwnerForName(name: String): DBIO[Option[ProviderOwner]] =
    getProviderOwnerForNameQuery(name).result.headOption

  private[this] lazy val getProviderOwnerForExtQuery = Compiled { (extProviderOwnerId: Rep[String]) =>
    ProviderOwnerTable.table.filter(_.extProviderOwnerId === extProviderOwnerId)
  }

  private[common] def getProviderOwnerForExt(extProviderOwnerId: String): DBIO[Option[ProviderOwner]] =
    getProviderOwnerForExtQuery(extProviderOwnerId).result.headOption

  private[this] def addProviderOwnerForId(
    extProviderOwnerId: String,
    name: String
  ) = sql"SELECT insert_or_get_provider_owner_id($extProviderOwnerId, $name)".as[Long]

  private[common] def addAndGetProviderOwner(
    extProviderOwnerId: String,
    name: String
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[ProviderOwner] = for {
    providerOwnerId <- addProviderOwnerForId(
      extProviderOwnerId,
      name
    ).head
    providerOwner <- getProviderOwnerQuery(providerOwnerId).result.head
  } yield providerOwner

  def upsertProviderOwner(providerOwner: ProviderOwner)(
    implicit
    ec: ExecutionContext
  ): DBIO[ProviderOwner] =
    providerOwner.providerOwnerId.fold[DBIO[ProviderOwner]](
      (ProviderOwnerTable.table returning ProviderOwnerTable.table) += providerOwner
    ) { (providerOwnerId: Long) => for {
      _ <- ProviderOwnerTable
        .table
        .filter(_.providerOwnerId === providerOwnerId)
        .update(providerOwner)
      updated <- ProviderOwnerDAO.getProviderOwner(providerOwnerId)
    } yield updated.get}

  private[common] def getOrAddProviderOwner(providerOwner: ProviderOwner)(
    implicit
    ec: ExecutionContext
  ): DBIO[ProviderOwner] = (providerOwner match {
    case ProviderOwner(Some(providerOwnerId), _, _) =>
      getProviderOwnerQuery(providerOwnerId).result.head
    case ProviderOwner(None, extProviderOwnerId, _) => for {
      exists <- getProviderOwnerForExt(extProviderOwnerId)
      po <- exists.fold(upsertProviderOwner(providerOwner))(DBIO.successful)
    } yield po
    case _ =>
      (ProviderOwnerTable.table returning ProviderOwnerTable.table) += providerOwner
  }).transactionally
}
