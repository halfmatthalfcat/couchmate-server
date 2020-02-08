package com.couchmate.db.dao

import com.couchmate.common.models.Channel
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.ChannelQueries
import com.couchmate.db.table.ChannelTable

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
