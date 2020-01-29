package com.couchmate.data.schema

import com.couchmate.data.models.ProviderChannel
import PgProfile.api._
import slick.lifted.{AppliedCompiledFunction, Tag}
import slick.migration.api.TableMigration
import slick.sql.SqlStreamingAction

import scala.concurrent.{ExecutionContext, Future}

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

  private[this] lazy val getProviderCompiled = Compiled { (providerChannelId: Rep[Long]) =>
    providerChannelTable.filter(_.providerChannelId === providerChannelId)
  }

  def getProviderChannel(providerChannelId: Long): AppliedCompiledFunction[Long, Query[ProviderChannelDAO, ProviderChannel, Seq], Seq[ProviderChannel]] = {
    getProviderCompiled(providerChannelId)
  }

  private[this] lazy val getProviderChannelForProviderAndChannelCompiled = Compiled {
    (providerId: Rep[Long], channelId: Rep[Long]) =>
      providerChannelTable.filter { providerChannel =>
        providerChannel.providerId === providerId &&
        providerChannel.channelId === channelId
      }
  }

  def getProviderChannelForProviderAndChannel(
    providerId: Long,
    channelId: Long,
  ): AppliedCompiledFunction[(Long, Long), Query[ProviderChannelDAO, ProviderChannel, Seq], Seq[ProviderChannel]] = {
    getProviderChannelForProviderAndChannelCompiled(providerId, channelId)
  }

  def upsertProviderChannel(pc: ProviderChannel): SqlStreamingAction[Vector[ProviderChannel], ProviderChannel, Effect] = {
    sql"""
         INSERT INTO provider_channel
         (provider_channel_id, provider_id, channel_id, channel)
         VALUES
         (${pc.providerChannelId}, ${pc.providerId}, ${pc.channelId}, ${pc.channel})
         ON CONFLICT (provider_channel_id)
         DO UPDATE SET
            provider_id = ${pc.providerId},
            channel_id = ${pc.channelId},
            channel = ${pc.channel}
         RETURNING *
       """.as[ProviderChannel]
  }
}
