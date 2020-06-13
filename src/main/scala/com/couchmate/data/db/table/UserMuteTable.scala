package com.couchmate.data.db.table

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.UserMute
import slick.lifted.Tag
import slick.migration.api._

import scala.concurrent.ExecutionContext

class UserMuteTable(tag: Tag) extends Table[UserMute](tag, "user_mute") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def userMuteId: Rep[UUID] = column[UUID]("user_mute_id", O.SqlType("uuid"))

  def * = (
    userId,
    userMuteId,
  ) <> ((UserMute.apply _).tupled, UserMute.unapply)

  def userIdFk = foreignKey(
    "user_id_fk",
    userId,
    UserTable.table
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def userMuteIdFk = foreignKey(
    "user_mute_id_fk",
    userMuteId,
    UserTable.table
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
}

object UserMuteTable extends Slickable[UserMuteTable] {
  private[db] val table = TableQuery[UserMuteTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.userMuteId,
    ).addForeignKeys(
      _.userIdFk,
      _.userMuteIdFk
    )

  private[db] def seed(implicit ec: ExecutionContext): Option[DBIO[_]] =
    Option.empty
}
