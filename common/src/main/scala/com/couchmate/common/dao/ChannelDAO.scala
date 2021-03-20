package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.Channel
import com.couchmate.common.tables.{AiringTable, ChannelTable, LineupTable, ProviderChannelTable, ProviderTable}

import scala.concurrent.{ExecutionContext, Future}

trait ChannelDAO {

  def getChannel(channelId: Long)(
    implicit
    db: Database
  ): Future[Option[Channel]] =
    db.run(ChannelDAO.getChannel(channelId))

  def getChannel$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Channel], NotUsed] =
    Slick.flowWithPassThrough(ChannelDAO.getChannel)

  def getChannelForExt(extId: Long)(
    implicit
    db: Database
  ): Future[Option[Channel]] =
    db.run(ChannelDAO.getChannelForExt(extId))

  def getChannelForProviderChannel(providerChannelId: Long)(
    implicit
    db: Database
  ): Future[Option[Channel]] =
    db.run(ChannelDAO.getChannelForProviderChannel(providerChannelId))

  def getChannelForExt$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Channel], NotUsed] =
    Slick.flowWithPassThrough(ChannelDAO.getChannelForExt)

  def upsertChannel(channel: Channel)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Channel] =
    db.run(ChannelDAO.upsertChannel(channel))

  def upsertChannel$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[Channel, Channel, NotUsed] =
    Slick.flowWithPassThrough(ChannelDAO.upsertChannel)
}

object ChannelDAO {
  private[this] lazy val getChannelQuery = Compiled { (channelId: Rep[Long]) =>
    ChannelTable.table.filter(_.channelId === channelId)
  }

  private[common] def getChannel(channelId: Long): DBIO[Option[Channel]] =
    getChannelQuery(channelId).result.headOption

  private[this] lazy val getChannelForExtQuery = Compiled { (extId: Rep[Long]) =>
    ChannelTable.table.filter(_.extId === extId)
  }

  private[common] def getChannelForExt(extId: Long): DBIO[Option[Channel]] =
    getChannelForExtQuery(extId).result.headOption

  private[this] lazy val getChannelForProviderChannelQuery = Compiled {
    (providerChannelId: Rep[Long]) => for {
      pc <- ProviderChannelTable.table if pc.providerChannelId === providerChannelId
      c <- ChannelTable.table if c.channelId === pc.channelId
    } yield c
  }

  private[common] def getChannelForProviderChannel(providerChannelId: Long): DBIO[Option[Channel]] =
    getChannelForProviderChannelQuery(providerChannelId).result.headOption

  private[this] lazy val getChannelForProviderAndAiringQuery = Compiled {
    (providerId: Rep[Long], airingId: Rep[String]) => for {
      l <- LineupTable.table if l.airingId === airingId
      pc <- ProviderChannelTable.table if (
        pc.providerChannelId === l.providerChannelId &&
        pc.providerId === providerId
      )
      c <- ChannelTable.table if c.channelId === pc.channelId
    } yield c
  }

  private[common] def getChannelForProviderAndAiring(providerId: Long, airingId: String): DBIO[Option[Channel]] =
    getChannelForProviderAndAiringQuery(providerId, airingId).result.headOption

  private[common] def upsertChannel(channel: Channel)(
    implicit
    ec: ExecutionContext
  ): DBIO[Channel] =
    channel.channelId.fold[DBIO[Channel]](
      (ChannelTable.table returning ChannelTable.table) += channel
    ) { (channelId: Long) => for {
      _ <- ChannelTable
        .table
        .filter(_.channelId === channelId)
        .update(channel)
      updated <- getChannel(channelId)
    } yield updated.get}

  private[common] def getOrAddChannel(channel: Channel)(
    implicit
    ec: ExecutionContext
  ): DBIO[Channel] = (channel match {
    case Channel(Some(channelId), extId, _, _) => for {
      exists <- getChannel(channelId) flatMap {
        case Some(c) => DBIO.successful(Some(c))
        case None => getChannelForExt(extId)
      }
      c <- exists.fold(upsertChannel(channel))(DBIO.successful)
    } yield c
    case Channel(None, extId, _, _) => for {
      exists <- getChannelForExt(extId)
      c <- exists.fold(upsertChannel(channel))(DBIO.successful)
    } yield c
  })
}
