package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ProviderOwnerTable
import com.couchmate.data.models.ProviderOwner

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
}

object ProviderOwnerDAO {
  private[this] lazy val getProviderOwnerQuery = Compiled { (providerOwnerId: Rep[Long]) =>
    ProviderOwnerTable.table.filter(_.providerOwnerId === providerOwnerId)
  }

  private[dao] def getProviderOwner(providerOwnerId: Long): DBIO[Option[ProviderOwner]] =
    getProviderOwnerQuery(providerOwnerId).result.headOption

  private[this] lazy val getProviderOwnerForNameQuery = Compiled { (name: Rep[String]) =>
    ProviderOwnerTable.table.filter(_.name === name)
  }

  private[dao] def getProviderOwnerForName(name: String): DBIO[Option[ProviderOwner]] =
    getProviderOwnerForNameQuery(name).result.headOption

  private[this] lazy val getProviderOwnerForExtQuery = Compiled { (extProviderOwnerId: Rep[String]) =>
    ProviderOwnerTable.table.filter(_.extProviderOwnerId === extProviderOwnerId)
  }

  private[dao] def getProviderOwnerForExt(extProviderOwnerId: String): DBIO[Option[ProviderOwner]] =
    getProviderOwnerForExtQuery(extProviderOwnerId).result.headOption

  private[dao] def upsertProviderOwner(providerOwner: ProviderOwner)(
    implicit
    ec: ExecutionContext
  ): DBIO[ProviderOwner] =
    providerOwner.providerOwnerId.fold[DBIO[ProviderOwner]](
      (ProviderOwnerTable.table returning ProviderOwnerTable.table) += providerOwner
    ) { (providerOwnerId: Long) => for {
      _ <- ProviderOwnerTable.table.update(providerOwner)
      updated <- ProviderOwnerDAO.getProviderOwner(providerOwnerId)
    } yield updated.get}

  private[dao] def getOrAddProviderOwner(providerOwner: ProviderOwner)(
    implicit
    ec: ExecutionContext
  ): DBIO[ProviderOwner] = (providerOwner match {
    case ProviderOwner(Some(providerOwnerId), _, _) =>
      getProviderOwnerQuery(providerOwnerId).result.head
    case ProviderOwner(None, Some(extProviderOwnerId), _) => for {
      exists <- getProviderOwnerForExt(extProviderOwnerId)
      po <- exists.fold(upsertProviderOwner(providerOwner))(DBIO.successful)
    } yield po
    case _ =>
      (ProviderOwnerTable.table returning ProviderOwnerTable.table) += providerOwner
  }).transactionally
}
