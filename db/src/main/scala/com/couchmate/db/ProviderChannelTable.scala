package com.couchmate.db

import com.couchmate.common.models.ProviderChannel
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class ProviderChannelTable(tag: Tag) extends Table[ProviderChannel](tag, "provider_channel") {
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
    ProviderTable.table,
    )(
    _.providerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def channelFk = foreignKey(
    "provider_channel_channel_fk",
    channelId,
    ChannelTable.table,
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

object ProviderChannelTable extends Slickable[ProviderChannelTable] {
  val table = TableQuery[ProviderChannelTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
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
}
