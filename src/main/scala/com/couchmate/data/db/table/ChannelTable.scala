package com.couchmate.data.db.table

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.Channel
import slick.lifted.Tag
import slick.migration.api._

import scala.concurrent.ExecutionContext

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

object ChannelTable extends Slickable[ChannelTable] {
  private[db] val table = TableQuery[ChannelTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.channelId,
      _.extId,
      _.channelOwnerId,
      _.callsign
    )
    .addForeignKeys(
      _.channelOwnerIdFk
    )

  private[db] def seed(implicit ec: ExecutionContext): Option[DBIO[_]] =
    Option.empty
}
