package com.couchmate.data.db.table

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.User
import slick.lifted.Tag
import slick.migration.api._

class UserTable(tag: Tag) extends Table[User](tag, "user") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey, O.SqlType("uuid"))
  def username: Rep[String] = column[String]("username")
  def active: Rep[Boolean] = column[Boolean]("active", O.Default(true))
  def verified: Rep[Boolean] = column[Boolean]("verified", O.Default(false))
  def * = (
    userId.?,
    username,
    active,
    verified,
  ) <> ((User.apply _).tupled, User.unapply)
}

object UserTable extends Slickable[UserTable] {
  private[db] val table: TableQuery[UserTable] = TableQuery[UserTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.username,
      _.active,
      _.verified,
    )
}
