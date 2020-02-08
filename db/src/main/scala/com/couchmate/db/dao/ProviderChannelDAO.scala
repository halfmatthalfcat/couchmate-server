package com.couchmate.db.dao

import com.couchmate.common.models.ProviderChannel
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.ProviderChannelQueries
import com.couchmate.db.table.ProviderChannelTable

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
