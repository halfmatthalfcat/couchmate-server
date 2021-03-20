package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{Channel, ChannelOwner, ProviderChannel}
import com.couchmate.common.tables.{AiringTable, LineupTable, ProviderChannelTable}

import scala.concurrent.{ExecutionContext, Future}

trait ProviderChannelDAO {

  def getProviderChannel(providerChannelId: Long)(
    implicit
    db: Database
  ): Future[Option[ProviderChannel]] =
    db.run(ProviderChannelDAO.getProviderChannel(providerChannelId))

  def getProviderChannel$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[ProviderChannel], NotUsed] =
    Slick.flowWithPassThrough(ProviderChannelDAO.getProviderChannel)

  def getProviderChannelForProviderAndChannel(providerId: Long, channelId: Long)(
    implicit
    db: Database
  ): Future[Option[ProviderChannel]] =
    db.run(ProviderChannelDAO.getProviderChannelForProviderAndChannel(providerId, channelId))

  def getProviderChannelForProviderAndChannel$()(
    implicit
    session: SlickSession
  ): Flow[(Long, Long), Option[ProviderChannel], NotUsed] =
    Slick.flowWithPassThrough(
      (ProviderChannelDAO.getProviderChannelForProviderAndChannel _).tupled
    )

  def getProviderChannelForProviderAndAiring(
    providerId: Long,
    airingId: String
  )(implicit db: Database): Future[Option[ProviderChannel]] =
    db.run(ProviderChannelDAO.getProviderChannelForProviderAndAiring(providerId, airingId))

  def upsertProviderChannel(providerChannel: ProviderChannel)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[ProviderChannel] =
    db.run(ProviderChannelDAO.upsertProviderChannel(providerChannel))

  def upsertProviderChannel$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[ProviderChannel, ProviderChannel, NotUsed] =
    Slick.flowWithPassThrough(ProviderChannelDAO.upsertProviderChannel)

  def getOrAddChannel(
    providerId: Long,
    channelNumber: String,
    channelOwner: Option[ChannelOwner],
    channel: Channel
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[ProviderChannel] =
    db.run(ProviderChannelDAO.getOrAddChannel(
      providerId,
      channelNumber,
      channelOwner,
      channel
    ))
}

object ProviderChannelDAO {
  private[this] lazy val getProviderChannelQuery = Compiled { (providerChannelId: Rep[Long]) =>
    ProviderChannelTable.table.filter(_.providerChannelId === providerChannelId)
  }

  private[common] def getProviderChannel(providerChannelId: Long): DBIO[Option[ProviderChannel]] =
    getProviderChannelQuery(providerChannelId).result.headOption

  private[this] lazy val getProviderChannelForProviderAndChannelQuery = Compiled {
    (providerId: Rep[Long], channelId: Rep[Long]) =>
      ProviderChannelTable.table.filter { pc =>
        pc.providerId === providerId &&
        pc.channelId === channelId
      }
  }

  private[common] def getProviderChannelForProviderAndChannel(
    providerId: Long,
    channelId: Long
  ): DBIO[Option[ProviderChannel]] =
    getProviderChannelForProviderAndChannelQuery(providerId, channelId).result.headOption

  private[this] lazy val getProviderChannelForProviderAndAiringQuery = Compiled {
    (providerId: Rep[Long], airingId: Rep[String]) => for {
      l <- LineupTable.table if l.airingId === airingId
      pc <- ProviderChannelTable.table if (
        pc.providerChannelId === l.providerChannelId &&
        pc.providerId === providerId
      )
    } yield pc
  }

  private[common] def getProviderChannelForProviderAndAiring(
    providerId: Long,
    airingId: String
  ): DBIO[Option[ProviderChannel]] =
    getProviderChannelForProviderAndAiringQuery(providerId, airingId).result.headOption

  private[common] def upsertProviderChannel(providerChannel: ProviderChannel)(
    implicit
    ec: ExecutionContext
  ): DBIO[ProviderChannel] =
    providerChannel.providerChannelId.fold[DBIO[ProviderChannel]](
      (ProviderChannelTable.table returning ProviderChannelTable.table) += providerChannel
    ) { (providerChannelId: Long) => for {
      _ <- ProviderChannelTable
        .table
        .filter(_.providerChannelId === providerChannelId)
        .update(providerChannel)
      updated <- ProviderChannelDAO.getProviderChannel(providerChannelId)
    } yield updated.get}

  private[common] def getOrAddChannel(
    providerId: Long,
    channelNumber: String,
    channelOwner: Option[ChannelOwner],
    channel: Channel
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[ProviderChannel] = (for {
    cO <- channelOwner
      .fold[DBIOAction[Option[ChannelOwner], NoStream, Effect.All]](
        DBIO.successful(Option.empty[ChannelOwner])
      )(owner =>
        ChannelOwnerDAO
          .getOrAddChannelOwner(owner)
          .map(o => Option(o))
      )
    c <- cO.fold(ChannelDAO.getOrAddChannel(channel))(owner =>
      ChannelDAO.getOrAddChannel(channel.copy(
        channelOwnerId = owner.channelOwnerId
      )))
    pcExists <- getProviderChannelForProviderAndChannel(providerId, c.channelId.get)
    pc <- pcExists.fold(upsertProviderChannel(ProviderChannel(
      providerChannelId = None,
      providerId = providerId,
      channelId = c.channelId.get,
      channel = channelNumber
    )))(DBIO.successful)
  } yield pc)
}
