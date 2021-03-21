package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserChannelFavorite
import com.couchmate.common.util.slick.WithTableQuery

import java.util.UUID

class UserChannelFavoriteTable(tag: Tag) extends Table[UserChannelFavorite](tag, "user_channel_favorite") {
  def userId: Rep[UUID] = column("user_id")
  def providerChannelId: Rep[Long] = column("provider_channel_id")
  def * = (
    userId,
    providerChannelId
  ) <> ((UserChannelFavorite.apply _).tupled, UserChannelFavorite.unapply)

  def pk = primaryKey(
    "user_channel_favorite_pk",
    (userId, providerChannelId)
  )

  def userFk = foreignKey(
    "user_channel_favorite_user_fk",
    userId,
    UserTable.table
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def providerChannelFk = foreignKey(
    "user_channel_favorite_provider_channel_fk",
    providerChannelId,
    ProviderChannelTable.table
  )(
    _.providerChannelId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
}

object UserChannelFavoriteTable extends WithTableQuery[UserChannelFavoriteTable] {
  private[couchmate] val table = TableQuery[UserChannelFavoriteTable]
}