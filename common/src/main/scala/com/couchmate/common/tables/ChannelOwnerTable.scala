package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.ChannelOwner
import com.couchmate.common.util.slick.WithTableQuery

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

object ChannelOwnerTable extends WithTableQuery[ChannelOwnerTable] {
  private[couchmate] val table = TableQuery[ChannelOwnerTable]
}
