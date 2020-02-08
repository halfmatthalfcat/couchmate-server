package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.ProviderChannelQueries
import com.couchmate.data.db.table.ProviderChannelTable
import com.couchmate.data.models.ProviderChannel

import scala.concurrent.{ExecutionContext, Future}

class ProviderChannelDAO(db: Database)(
  implicit
  ec: ExecutionContext
) extends ProviderChannelQueries {

  def getProviderChannel(providerChannelId: Long): Future[Option[ProviderChannel]] = {
    db.run(super.getProviderChannel(providerChannelId).result.headOption)
  }

  def getProviderChannelForProviderAndChannel(providerId: Long, channelId: Long): Future[Option[ProviderChannel]] = {
    db.run(super.getProviderChannelForProviderAndChannel(providerId, channelId).result.headOption)
  }

  def upsertProviderChannel(providerChannel: ProviderChannel): Future[ProviderChannel] =
    providerChannel.providerChannelId.fold(
      db.run((ProviderChannelTable.table returning ProviderChannelTable.table) += providerChannel)
    ) { (providerChannelId: Long) => db.run(for {
      _ <- ProviderChannelTable.table.update(providerChannel)
      updated <- super.getProviderChannelForProviderAndChannel(providerChannelId)
    } yield updated.result.head.transactionally)}

}
