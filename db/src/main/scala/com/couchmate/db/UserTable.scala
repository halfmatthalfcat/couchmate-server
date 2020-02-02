package com.couchmate.db

import java.util.UUID

import com.couchmate.common.models.User
import com.couchmate.db.PgProfile.api._
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
  val table: TableQuery[UserTable] = TableQuery[UserTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.username,
      _.active,
      _.verified,
    )
}
