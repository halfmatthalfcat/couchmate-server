package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.ChannelQueries
import com.couchmate.data.db.table.ChannelTable
import com.couchmate.data.models.Channel

import scala.concurrent.{ExecutionContext, Future}

class ChannelDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends ChannelQueries {

  def getChannel(channelId: Long): Future[Option[Channel]] = {
    db.run(super.getChannel(channelId).result.headOption)
  }

  def getChannelForExt(extId: Long): Future[Option[Channel]] = {
    db.run(super.getChannelForExt(extId).result.headOption)
  }

  def upsertChannel(channel: Channel): Future[Channel] =
    channel.channelId.fold(
      db.run((ChannelTable.table returning ChannelTable.table) += channel)
    ) { (channelId: Long) => db.run(for {
      _ <- ChannelTable.table.update(channel)
      updated <- super.getChannel(channelId)
    } yield updated.result.head.transactionally)}

}
