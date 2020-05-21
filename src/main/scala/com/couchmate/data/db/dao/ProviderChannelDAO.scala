package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ProviderChannelTable
import com.couchmate.data.models.{Channel, ProviderChannel}
import com.couchmate.external.gracenote.models.GracenoteChannelAiring
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

class ProviderChannelDAO(db: Database)(
  implicit
  ec: ExecutionContext
) {

  def getProviderChannel(providerChannelId: Long): Future[Option[ProviderChannel]] = {
    db.run(ProviderChannelDAO.getProviderChannel(providerChannelId).result.headOption)
  }

  def getProviderChannelForProviderAndChannel(providerId: Long, channelId: Long): Future[Option[ProviderChannel]] = {
    db.run(ProviderChannelDAO.getProviderChannelForProviderAndChannel(providerId, channelId).result.headOption)
  }

  def upsertProviderChannel(providerChannel: ProviderChannel): Future[ProviderChannel] = db.run(
    providerChannel.providerChannelId.fold[DBIO[ProviderChannel]](
      (ProviderChannelTable.table returning ProviderChannelTable.table) += providerChannel
    ) { (providerChannelId: Long) => for {
      _ <- ProviderChannelTable.table.update(providerChannel)
      updated <- ProviderChannelDAO.getProviderChannel(providerChannelId).result.head
    } yield updated}.transactionally
  )

  def getProviderChannelFromGracenote(
    channel: Channel,
    channelAiring: GracenoteChannelAiring,
  ): Future[ProviderChannel] = db.run((for {
    exists <- ProviderChannelDAO.getProviderChannelForProviderAndChannel(
      channelAiring.providerId.get,
      channel.channelId.get,
    ).result.headOption
    pc <- exists.fold[DBIO[ProviderChannel]](
      (ProviderChannelTable.table returning ProviderChannelTable.table) += ProviderChannel(
        providerChannelId = None,
        channelAiring.providerId.get,
        channel.channelId.get,
        channelAiring.channel,
      )
    )(DBIO.successful)
  } yield pc).transactionally)

}

object ProviderChannelDAO {
  private[dao] lazy val getProviderChannel = Compiled { (providerChannelId: Rep[Long]) =>
    ProviderChannelTable.table.filter(_.providerChannelId === providerChannelId)
  }

  private[dao] lazy val getProviderChannelForProviderAndChannel = Compiled {
    (providerId: Rep[Long], channelId: Rep[Long]) =>
      ProviderChannelTable.table.filter { pc =>
        pc.providerId === providerId &&
        pc.channelId === channelId
      }
  }
}
