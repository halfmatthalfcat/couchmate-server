package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ProviderChannelTable
import com.couchmate.data.models.{Channel, ProviderChannel}
import com.couchmate.external.gracenote.models.GracenoteChannelAiring
import slick.lifted.Compiled

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

  def getProviderChannelFromGracenote(
    channel: Channel,
    channelAiring: GracenoteChannelAiring,
  )(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[ProviderChannel] =
    db.run(ProviderChannelDAO.getProviderChannelFromGracenote(channel, channelAiring))

  def getProviderChannelFromGracenote$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[(Channel, GracenoteChannelAiring), ProviderChannel, NotUsed] =
    Slick.flowWithPassThrough(
      (ProviderChannelDAO.getProviderChannelFromGracenote _).tupled
    )

}

object ProviderChannelDAO {
  private[this] lazy val getProviderChannelQuery = Compiled { (providerChannelId: Rep[Long]) =>
    ProviderChannelTable.table.filter(_.providerChannelId === providerChannelId)
  }

  private[dao] def getProviderChannel(providerChannelId: Long): DBIO[Option[ProviderChannel]] =
    getProviderChannelQuery(providerChannelId).result.headOption

  private[this] lazy val getProviderChannelForProviderAndChannelQuery = Compiled {
    (providerId: Rep[Long], channelId: Rep[Long]) =>
      ProviderChannelTable.table.filter { pc =>
        pc.providerId === providerId &&
        pc.channelId === channelId
      }
  }

  private[dao] def getProviderChannelForProviderAndChannel(
    providerId: Long,
    channelId: Long
  ): DBIO[Option[ProviderChannel]] =
    getProviderChannelForProviderAndChannelQuery(providerId, channelId).result.headOption

  private[dao] def upsertProviderChannel(providerChannel: ProviderChannel)(
    implicit
    ec: ExecutionContext
  ): DBIO[ProviderChannel] =
    providerChannel.providerChannelId.fold[DBIO[ProviderChannel]](
      (ProviderChannelTable.table returning ProviderChannelTable.table) += providerChannel
    ) { (providerChannelId: Long) => for {
      _ <- ProviderChannelTable.table.update(providerChannel)
      updated <- ProviderChannelDAO.getProviderChannel(providerChannelId)
    } yield updated.get}

  private[dao] def getProviderChannelFromGracenote(
    channel: Channel,
    channelAiring: GracenoteChannelAiring
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[ProviderChannel] = for {
    exists <- getProviderChannelForProviderAndChannel(
      channelAiring.providerId.get,
      channel.channelId.get,
    )
    pc <- exists.fold[DBIO[ProviderChannel]](
      (ProviderChannelTable.table returning ProviderChannelTable.table) += ProviderChannel(
        providerChannelId = None,
        channelAiring.providerId.get,
        channel.channelId.get,
        channelAiring.channel,
      )
    )(DBIO.successful)
  } yield pc
}
