package com.couchmate.db

import java.util.UUID

import com.couchmate.common.models.UserPrivate
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class UserPrivateTable(tag: Tag) extends Table[UserPrivate](tag, "user_private") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def password: Rep[String] = column[String]("password")
  def * = (
    userId,
    password,
  ) <> ((UserPrivate.apply _).tupled, UserPrivate.unapply)

  def userFk = foreignKey(
    "user_user_private_fk",
    userId,
    UserTable.table,
    )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserPrivateTable extends Slickable[UserPrivateTable] {
  val table = TableQuery[UserPrivateTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.password,
    ).addForeignKeys(
      _.userFk,
    )
}
