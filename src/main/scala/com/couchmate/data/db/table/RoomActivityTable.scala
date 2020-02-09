package com.couchmate.data.db.table

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.{RoomActivity, RoomActivityType}
import slick.lifted.Tag
import slick.migration.api._

class RoomActivityTable(tag: Tag) extends Table[RoomActivity](tag, "room_activity") {
  def airingId: Rep[UUID] = column[UUID]("airing_id", O.SqlType("uuid"))
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def action: Rep[RoomActivityType] = column[RoomActivityType]("action")
  def created: Rep[LocalDateTime] = column[LocalDateTime]("created", O.SqlType("timestamp"))
  def * = (
    airingId,
    userId,
    action,
    created,
  ) <> ((RoomActivity.apply _).tupled, RoomActivity.unapply)

  def airingFk = foreignKey(
    "room_airing_fk",
    airingId,
    AiringTable.table,
    )(
    _.airingId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def userFk = foreignKey(
    "room_user_fk",
    userId,
    UserTable.table,
    )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def roomActivityIdx = index(
    "room_activity_active_idk",
    (airingId, action, created),
  )
}

object RoomActivityTable extends Slickable[RoomActivityTable] {
  private[db] val table = TableQuery[RoomActivityTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.airingId,
      _.userId,
      _.action,
      _.created,
    ).addForeignKeys(
      _.airingFk,
      _.userFk,
    ).addIndexes(
      _.roomActivityIdx,
    )
}
