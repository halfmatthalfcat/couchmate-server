package com.couchmate.common.tables

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserMeta
import com.couchmate.common.util.slick.WithTableQuery

class UserMetaTable(tag: Tag) extends Table[UserMeta](tag, "user_meta") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey, O.SqlType("uuid"))
  def email: Rep[Option[String]] = column[Option[String]]("email")
  def username: Rep[String] = column[String]("username")
  def * = (
    userId,
    username,
    email,
  ) <> ((UserMeta.apply _).tupled, UserMeta.unapply)

  def userFk = foreignKey(
    "user_user_meta_fk",
    userId,
    UserTable.table,
    )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserMetaTable extends WithTableQuery[UserMetaTable] {
  private[couchmate] val table = TableQuery[UserMetaTable]
}
