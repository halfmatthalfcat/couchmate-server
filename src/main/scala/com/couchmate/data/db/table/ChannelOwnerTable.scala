package com.couchmate.data.db.table

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.ChannelOwner
import slick.migration.api.TableMigration

class ChannelOwnerTable(tag: Tag) extends Table[ChannelOwner](tag, "channel_owner") {
  def channelOwnerId: Rep[Long] = column[Long]("channel_owner_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id")
  def callsign: Rep[String] = column[String]("callsign")

  def * = (
    channelOwnerId.?,
    extId,
    callsign
  ) <> ((ChannelOwner.apply _).tupled, ChannelOwner.unapply)
}

object ChannelOwnerTable extends Slickable[ChannelOwnerTable] {
  private[db] val table = TableQuery[ChannelOwnerTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.channelOwnerId,
      _.extId,
      _.callsign
    )
}
