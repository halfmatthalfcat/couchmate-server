package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.Channel
import com.couchmate.common.util.slick.WithTableQuery

class ChannelTable(tag: Tag) extends Table[Channel](tag, "channel") {
  def channelId: Rep[Long] = column[Long]("channel_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id")
  def channelOwnerId: Rep[Option[Long]] = column[Option[Long]]("channel_owner_id")
  def callsign: Rep[String] = column[String]("callsign")
  def * = (
    channelId.?,
    extId,
    channelOwnerId,
    callsign,
  ) <> ((Channel.apply _).tupled, Channel.unapply)

  def channelOwnerIdFk = foreignKey(
    "channel_channel_owner_fk",
    channelOwnerId,
    ChannelOwnerTable.table,
  )(
    _.channelOwnerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
}

object ChannelTable extends WithTableQuery[ChannelTable] {
  private[couchmate] val table = TableQuery[ChannelTable]
}
