package com.couchmate.db

import com.couchmate.common.models.Channel
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class ChannelTable(tag: Tag) extends Table[Channel](tag, "channel") {
  def channelId: Rep[Long] = column[Long]("channel_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id")
  def callsign: Rep[String] = column[String]("callsign")
  def * = (
    channelId.?,
    extId,
    callsign,
  ) <> ((Channel.apply _).tupled, Channel.unapply)
}

object ChannelTable extends Slickable[ChannelTable] {
  val table = TableQuery[ChannelTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.channelId,
      _.extId,
      _.callsign
    )
}
