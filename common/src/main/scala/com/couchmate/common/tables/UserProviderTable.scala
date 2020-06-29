package com.couchmate.common.tables

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserProvider
import com.couchmate.common.util.slick.WithTableQuery

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

object UserProviderTable extends WithTableQuery[UserProviderTable] {
  private[couchmate] val table = TableQuery[UserProviderTable]
}
