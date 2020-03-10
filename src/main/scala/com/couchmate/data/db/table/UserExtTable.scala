package com.couchmate.data.db.table

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.{UserExt, UserExtType}
import slick.lifted.Tag
import slick.migration.api._

class UserExtTable(tag: Tag) extends Table[UserExt](tag, "user_ext") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey, O.SqlType("uuid"))
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
  private[db] val table = TableQuery[UserExtTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.extType,
      _.extId,
    ).addForeignKeys(
      _.userFk,
    )
}
