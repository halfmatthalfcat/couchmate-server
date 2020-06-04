package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ChannelOwnerTable
import com.couchmate.data.models.ChannelOwner
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

trait ChannelOwnerDAO {
  def getChannelOwner(channelOwnerId: Long)(
    implicit
    db: Database
  ): Future[Option[ChannelOwner]] =
    db.run(ChannelOwnerDAO.getChannelOwner(channelOwnerId))

  def getChannelOwner$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[ChannelOwner], NotUsed] =
    Slick.flowWithPassThrough(ChannelOwnerDAO.getChannelOwner)

  def getChannelOwnerForExt(extId: Long)(
    implicit
    db: Database
  ): Future[Option[ChannelOwner]] =
    db.run(ChannelOwnerDAO.getChannelOwnerForExt(extId))

  def getChannelOwnerForExt$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[ChannelOwner], NotUsed] =
    Slick.flowWithPassThrough(ChannelOwnerDAO.getChannelOwnerForExt)

  def upsertChannelOwner(channelOwner: ChannelOwner)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[ChannelOwner] =
    db.run(ChannelOwnerDAO.upsertChannelOwner(channelOwner))

  def upsertChannelOwner$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[ChannelOwner, ChannelOwner, NotUsed] =
    Slick.flowWithPassThrough(ChannelOwnerDAO.upsertChannelOwner)
}

object ChannelOwnerDAO {
  private[this] lazy val getChannelOwnerQuery = Compiled { (channelOwnerId: Rep[Long]) =>
    ChannelOwnerTable.table.filter(_.channelOwnerId === channelOwnerId)
  }

  private[dao] def getChannelOwner(channelOwnerId: Long): DBIO[Option[ChannelOwner]] =
    getChannelOwnerQuery(channelOwnerId).result.headOption

  private[this] lazy val getChannelOwnerForExtQuery = Compiled { (extId: Rep[Long]) =>
    ChannelOwnerTable.table.filter(_.extId === extId)
  }

  private[dao] def getChannelOwnerForExt(extId: Long): DBIO[Option[ChannelOwner]] =
    getChannelOwnerForExtQuery(extId).result.headOption

  private[dao] def upsertChannelOwner(channelOwner: ChannelOwner)(
    implicit
    ec: ExecutionContext
  ): DBIO[ChannelOwner] =
    channelOwner.channelOwnerId.fold[DBIO[ChannelOwner]](
      (ChannelOwnerTable.table returning ChannelOwnerTable.table) += channelOwner
    ) { (channelOwnerId: Long) => for {
      _ <- ChannelOwnerTable
        .table
        .filter(_.channelOwnerId === channelOwnerId)
        .update(channelOwner)
      updated <- getChannelOwner(channelOwnerId)
    } yield updated.get}.transactionally

  private[dao] def getOrAddChannelOwner(channelOwner: ChannelOwner)(
    implicit
    ec: ExecutionContext
  ): DBIO[ChannelOwner] = (channelOwner match {
    case ChannelOwner(Some(channelOwnerId), extId, _) => for {
      exists <- getChannelOwner(channelOwnerId) flatMap {
        case Some(owner) => DBIO.successful(Some(owner))
        case None => getChannelOwnerForExt(extId)
      }
      owner <- exists.fold(upsertChannelOwner(channelOwner))(DBIO.successful)
    } yield owner
    case ChannelOwner(None, extId, _) => for {
      exists <- getChannelOwnerForExt(extId)
      owner <- exists.fold(upsertChannelOwner(channelOwner))(DBIO.successful)
    } yield owner
  }).transactionally
}
