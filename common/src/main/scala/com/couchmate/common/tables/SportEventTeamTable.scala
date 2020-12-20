package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.SportEventTeam
import com.couchmate.common.util.slick.WithTableQuery

class SportEventTeamTable(tag: Tag) extends Table[SportEventTeam](tag, "sport_event_team") {
  def sportEventId: Rep[Long] = column[Long]("sport_event_id")
  def sportTeamId: Rep[Long] = column[Long]("sport_team_id")
  def isHome: Rep[Boolean] = column[Boolean]("is_home")

  def * = (
    sportEventId,
    sportTeamId,
    isHome
  ) <> ((SportEventTeam.apply _).tupled, SportEventTeam.unapply)

  def sportEventTeamTablePk = primaryKey(
    "sport_event_team_pk",
    (sportEventId, sportTeamId)
  )

  def sportEventFk = foreignKey(
    "sport_event_team_sport_event_fk",
    sportEventId,
    SportEventTable.table
  )(
    _.sportEventId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def sportTeamFk = foreignKey(
    "sport_event_team_sport_team_fk",
    sportTeamId,
    SportTeamTable.table
  )(
    _.sportTeamId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
}

object SportEventTeamTable extends WithTableQuery[SportEventTeamTable] {
  private[couchmate] val table: TableQuery[SportEventTeamTable] =
    TableQuery[SportEventTeamTable]
}
