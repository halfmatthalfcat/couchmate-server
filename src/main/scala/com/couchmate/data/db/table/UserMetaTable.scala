package com.couchmate.data.db.table

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.{UserMeta, UserRole}
import slick.lifted.Tag
import slick.migration.api._

class UserMetaTable(tag: Tag) extends Table[UserMeta](tag, "user_meta") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def email: Rep[String] = column[String]("email")
  def role: Rep[UserRole] = column[UserRole]("role")
  def * = (
    userId,
    email,
    role,
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

object UserMetaTable extends Slickable[UserMetaTable] {
  private[db] val table = TableQuery[UserMetaTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.email,
    ).addForeignKeys(
      _.userFk,
    )
}
