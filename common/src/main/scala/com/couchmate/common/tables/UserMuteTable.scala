package com.couchmate.common.tables

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserMute
import com.couchmate.common.util.slick.WithTableQuery

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

object UserMuteTable extends WithTableQuery[UserMuteTable] {
  private[couchmate] val table = TableQuery[UserMuteTable]
}
