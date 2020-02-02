package com.couchmate.db

import java.util.UUID

import com.couchmate.common.models.{UserExt, UserExtType}
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class UserExtTable(tag: Tag) extends Table[UserExt](tag, "user_ext") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def extType: Rep[UserExtType] = column[UserExtType]("ext_type")
  def extId: Rep[String] = column[String]("ext_id")
  def * = (
    userId,
    extType,
    extId,
  ) <> ((UserExt.apply _).tupled, UserExt.unapply)

  def userFk = foreignKey(
    "user_user_ext_fk",
    userId,
    UserTable.table,
    )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserExtTable extends Slickable[UserExtTable] {
  val table = TableQuery[UserExtTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.extType,
      _.extId,
    ).addForeignKeys(
      _.userFk,
    )
}
