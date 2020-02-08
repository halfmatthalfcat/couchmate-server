package com.couchmate.data.db.table

import java.util.UUID

import com.couchmate.data.db.Slickable
import com.couchmate.data.models.Lineup
import com.couchmate.data.db.Slickable
import com.couchmate.data.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class LineupTable(tag: Tag) extends Table[Lineup](tag, "lineup") {
  def lineupId: Rep[Long] = column[Long]("lineup_id", O.AutoInc, O.PrimaryKey)
  def providerChannelId: Rep[Long] = column[Long]("provider_channel_id")
  def airingId: Rep[UUID] = column[UUID]("airing_id", O.SqlType("uuid"))
  def replacedBy: Rep[Option[UUID]] = column[Option[UUID]]("replaced_by", O.SqlType("uuid"))
  def * = (
    lineupId.?,
    providerChannelId,
    airingId,
    replacedBy,
  ) <> ((Lineup.apply _).tupled, Lineup.unapply)

  def providerChannelFk = foreignKey(
    "lineup_provider_channel_fk",
    providerChannelId,
    ProviderChannelTable.table,
    )(
    _.providerChannelId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def airingFk = foreignKey(
    "lineup_airing_fk",
    airingId,
    AiringTable.table,
    )(
    _.airingId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object LineupTable extends Slickable[LineupTable] {
  private[db] val table = TableQuery[LineupTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.lineupId,
      _.providerChannelId,
      _.airingId,
      _.replacedBy,
    ).addForeignKeys(
      _.providerChannelFk,
      _.airingFk,
    )
}
