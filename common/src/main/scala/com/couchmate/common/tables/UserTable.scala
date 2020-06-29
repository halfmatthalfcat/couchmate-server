package com.couchmate.common.tables

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{User, UserRole}
import com.couchmate.common.util.slick.WithTableQuery

class UserTable(tag: Tag) extends Table[User](tag, "user") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey, O.SqlType("uuid"))
  def role: Rep[UserRole] = column[UserRole]("role")
  def active: Rep[Boolean] = column[Boolean]("active", O.Default(true))
  def verified: Rep[Boolean] = column[Boolean]("verified", O.Default(false))
  def created: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("created", O.SqlType("timestamp default now()"))
  def * = (
    userId.?,
    role,
    active,
    verified,
    created
  ) <> ((User.apply _).tupled, User.unapply)
}

object UserTable extends WithTableQuery[UserTable] {
  private[couchmate] val table: TableQuery[UserTable] =
    TableQuery[UserTable]
}
