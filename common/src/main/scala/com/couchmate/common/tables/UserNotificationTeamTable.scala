package com.couchmate.common.tables

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserNotificationTeam
import com.couchmate.common.util.slick.WithTableQuery

class UserNotificationTeamTable(tag: Tag) extends Table[UserNotificationTeam](tag, "user_notification_team") {
  def userId: Rep[UUID] = column("user_id", O.SqlType("uuid"))
  def teamId: Rep[Long] = column("team_id")
  def providerId: Rep[Long] = column("provider_id")
  def name: Rep[String] = column("name")
  def hash: Rep[String] = column("hash")
  def active: Rep[Boolean] = column("active")
  def onlyNew: Rep[Boolean] = column("only_new")
  def created: Rep[LocalDateTime] = column("created")

  def * = (
    userId,
    teamId,
    providerId,
    name,
    hash,
    onlyNew,
    active,
    created
  ) <> ((UserNotificationTeam.apply _).tupled, UserNotificationTeam.unapply)

  def pk = primaryKey(
    "user_notification_team_pk",
    (userId, teamId)
  )

  def userIdFk = foreignKey(
    "user_notification_team_user_fk",
    userId,
    UserTable.table
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def teamIdFk = foreignKey(
    "user_notification_team_team_fk",
    teamId,
    SportOrganizationTeamTable.table
  )(
    _.sportOrganizationTeamId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def providerChannelFk = foreignKey(
    "user_notification_series_provider_fk",
    providerId,
    ProviderTable.table
  )(
    _.providerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  // deprecated
  def teamIdFkOld = foreignKey(
    "user_notification_team_team_fk",
    teamId,
    SportTeamTable.table
  )(
    _.sportTeamId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
}

object UserNotificationTeamTable extends WithTableQuery[UserNotificationTeamTable] {
  private[couchmate] val table: TableQuery[UserNotificationTeamTable] =
    TableQuery[UserNotificationTeamTable]
}
