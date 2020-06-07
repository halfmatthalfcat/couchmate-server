package com.couchmate.data.db.table

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.UserProvider
import slick.lifted.Tag
import slick.migration.api._

import scala.concurrent.ExecutionContext

class UserProviderTable(tag: Tag) extends Table[UserProvider](tag, "user_provider") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey)
  def providerId: Rep[Long] = column[Long]("provider_id")
  def * = (
    userId,
    providerId,
  ) <> ((UserProvider.apply _).tupled, UserProvider.unapply)

  def userFk = foreignKey(
    "user_provider_user_fk",
    userId,
    UserTable.table,
    )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def providerFk = foreignKey(
    "user_provider_provider_fk",
    providerId,
    ProviderTable.table,
    )(
    _.providerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserProviderTable extends Slickable[UserProviderTable] {
  private[db] val table = TableQuery[UserProviderTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.providerId,
    ).addForeignKeys(
      _.userFk,
      _.providerFk,
    )

  private[db] def seed(implicit ec: ExecutionContext): Option[DBIO[_]] =
    Option.empty
}
