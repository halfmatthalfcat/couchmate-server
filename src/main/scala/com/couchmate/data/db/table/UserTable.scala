package com.couchmate.data.db.table

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.{User, UserRole}
import slick.lifted.Tag
import slick.migration.api._

import scala.concurrent.ExecutionContext

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

object UserTable extends Slickable[UserTable] {
  private[db] val table: TableQuery[UserTable] = TableQuery[UserTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.role,
      _.active,
      _.verified,
      _.created
    )

  private[db] def seed(implicit ec: ExecutionContext): Option[DBIO[_]] =
    Option.empty
}
