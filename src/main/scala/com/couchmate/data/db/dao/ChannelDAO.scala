package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ChannelTable
import com.couchmate.data.models.Channel
import com.couchmate.external.gracenote.models.GracenoteChannelAiring
import slick.lifted.Compiled

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

  def getChannelFromGracenote(
    channelAiring: GracenoteChannelAiring,
  )(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Channel] =
    db.run(ChannelDAO.getChannelFromGracenote(channelAiring))

  def getChannelFromGracenote$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[GracenoteChannelAiring, Channel, NotUsed] =
    Slick.flowWithPassThrough(ChannelDAO.getChannelFromGracenote)
}

object ChannelDAO {
  private[this] lazy val getChannelQuery = Compiled { (channelId: Rep[Long]) =>
    ChannelTable.table.filter(_.channelId === channelId)
  }

  private[dao] def getChannel(channelId: Long): DBIO[Option[Channel]] =
    getChannelQuery(channelId).result.headOption

  private[this] lazy val getChannelForExtQuery = Compiled { (extId: Rep[Long]) =>
    ChannelTable.table.filter(_.extId === extId)
  }

  private[dao] def getChannelForExt(extId: Long): DBIO[Option[Channel]] =
    getChannelForExtQuery(extId).result.headOption

  private[dao] def upsertChannel(channel: Channel)(
    implicit
    ec: ExecutionContext
  ): DBIO[Channel] =
    channel.channelId.fold[DBIO[Channel]](
      (ChannelTable.table returning ChannelTable.table) += channel
    ) { (channelId: Long) => for {
      _ <- ChannelTable.table.update(channel)
      updated <- getChannel(channelId)
    } yield updated.get}

  private[dao] def getChannelFromGracenote(
    channelAiring: GracenoteChannelAiring
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Channel] = for {
    exists <- getChannelForExt(channelAiring.stationId)
    channel <- exists.fold[DBIO[Channel]](
      (ChannelTable.table returning ChannelTable.table) += Channel(
        channelId = None,
        channelAiring.stationId,
        channelAiring.affiliateCallSign
                     .getOrElse(channelAiring.callSign)
      )
    )(DBIO.successful)
  } yield channel
}
