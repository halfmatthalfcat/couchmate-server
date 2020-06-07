package com.couchmate.data.db.table

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.UserPrivate
import slick.lifted.Tag
import slick.migration.api._

import scala.concurrent.ExecutionContext

class UserPrivateTable(tag: Tag) extends Table[UserPrivate](tag, "user_private") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey, O.SqlType("uuid"))
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
  private[db] val table = TableQuery[UserPrivateTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.password,
    ).addForeignKeys(
      _.userFk,
    )

  private[db] def seed(implicit ec: ExecutionContext): Option[DBIO[_]] =
    Option.empty
}
