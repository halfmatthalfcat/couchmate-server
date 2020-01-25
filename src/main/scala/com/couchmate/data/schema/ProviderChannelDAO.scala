package com.couchmate.data.schema

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.ProviderChannel
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class ProviderChannelDAO(tag: Tag) extends Table[ProviderChannel](tag, "provider_channel") {
  def providerChannelId: Rep[Long] = column[Long]("provider_channel_id", O.PrimaryKey, O.AutoInc)
  def providerId: Rep[Long] = column[Long]("provider_id")
  def channelId: Rep[Long] = column[Long]("channel_id")
  def channel: Rep[String] = column[String]("channel")
  def * = (
    providerChannelId.?,
    providerId,
    channelId,
    channel
  ) <> ((ProviderChannel.apply _).tupled, ProviderChannel.unapply)

  def providerFk = foreignKey(
    "provider_channel_provider_fk",
    providerId,
    ProviderDAO.providerTable,
  )(
    _.providerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def channelFk = foreignKey(
    "provider_channel_channel_fk",
    channelId,
    ChannelDAO.channelTable,
  )(
    _.channelId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def providerChannelIdx = index(
    "provider_channel_idx",
    (providerId, channelId)
  )
}

object ProviderChannelDAO {
  val providerChannelTable = TableQuery[ProviderChannelDAO]

  val init = TableMigration(providerChannelTable)
    .create
    .addColumns(
      _.providerChannelId,
      _.providerId,
      _.channelId,
      _.channel,
    ).addForeignKeys(
      _.providerFk,
      _.channelFk,
    ).addIndexes(
      _.providerChannelIdx,
    )

  def getProviderChannel()(
    implicit
    session: SlickSession,
  ): Flow[Long, Option[ProviderChannel], NotUsed] = Slick.flowWithPassThrough { providerChannelId =>
    providerChannelTable.filter(_.providerChannelId === providerChannelId).result.headOption
  }

  def getProviderChannelForProviderAndChannel()(
    implicit
    session: SlickSession,
  ): Flow[(Long, Long), Option[ProviderChannel], NotUsed] = Slick.flowWithPassThrough {
    case (providerId, channelId) => providerChannelTable.filter { providerChannel =>
      providerChannel.providerId === providerId &&
      providerChannel.channelId === channelId
    }.result.headOption

  }

  def upsertProviderChannel()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[ProviderChannel, ProviderChannel, NotUsed] = Slick.flowWithPassThrough {
    case providerChannel @ ProviderChannel(None, _, _, _) =>
      (providerChannelTable returning providerChannelTable) += providerChannel
    case providerChannel @ ProviderChannel(Some(providerChannelId), _, _, _) => for {
      _ <- providerChannelTable.filter(_.providerChannelId === providerChannelId).update(providerChannel)
      pc <- providerChannelTable.filter(_.providerChannelId === providerChannelId).result.head
    } yield pc
  }
}
