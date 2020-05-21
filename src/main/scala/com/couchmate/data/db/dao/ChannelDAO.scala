package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ChannelTable
import com.couchmate.data.models.Channel
import com.couchmate.external.gracenote.models.GracenoteChannelAiring
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

class ChannelDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getChannel(channelId: Long): Future[Option[Channel]] = {
    db.run(ChannelDAO.getChannel(channelId).result.headOption)
  }

  def getChannelForExt(extId: Long): Future[Option[Channel]] = {
    db.run(ChannelDAO.getChannelForExt(extId).result.headOption)
  }

  def upsertChannel(channel: Channel): Future[Channel] = db.run(
    channel.channelId.fold[DBIO[Channel]](
      (ChannelTable.table returning ChannelTable.table) += channel
    ) { (channelId: Long) => for {
      _ <- ChannelTable.table.update(channel)
      updated <- ChannelDAO.getChannel(channelId).result.head
    } yield updated}.transactionally
  )

  def getChannelFromGracenote(
    channelAiring: GracenoteChannelAiring,
  ): Future[Channel] = db.run((for {
    exists <- ChannelDAO.getChannelForExt(channelAiring.stationId).result.headOption
    channel <- exists.fold[DBIO[Channel]](
      (ChannelTable.table returning ChannelTable.table) += Channel(
        channelId = None,
        channelAiring.stationId,
        channelAiring.affiliateCallSign
          .getOrElse(channelAiring.callSign)
      )
    )(DBIO.successful)
  } yield channel).transactionally)
}

object ChannelDAO {
  private[dao] lazy val getChannel = Compiled { (channelId: Rep[Long]) =>
    ChannelTable.table.filter(_.channelId === channelId)
  }

  private[dao] lazy val getChannelForExt = Compiled { (extId: Rep[Long]) =>
    ChannelTable.table.filter(_.extId === extId)
  }
}
