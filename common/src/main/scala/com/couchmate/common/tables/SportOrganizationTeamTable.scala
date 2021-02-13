package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.SportOrganizationTeam
import com.couchmate.common.util.slick.WithTableQuery

class SportOrganizationTeamTable(tag: Tag) extends Table[SportOrganizationTeam](tag, "sport_organization_team") {
  def sportOrganizationTeamId: Rep[Long] = column("sport_organization_team_id", O.AutoInc, O.PrimaryKey)
  def sportTeamId: Rep[Long] = column("sport_team_id")
  def sportOrganizationId: Rep[Long] = column("sport_organization_id")

  def * = (
    sportOrganizationId.?,
    sportTeamId,
    sportOrganizationId
  ) <> ((SportOrganizationTeam.apply _).tupled, SportOrganizationTeam.unapply)

  def sportTeamFk = foreignKey(
    "sport_organization_team_sport_team_fk",
    sportTeamId,
    SportTeamTable.table
  )(
    _.sportTeamId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def sportOrgFk = foreignKey(
    "sport_organization_team_org_fk",
    sportOrganizationId,
    SportOrganizationTable.table
  )(
    _.sportOrganizationId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def teamOrgIdx = index(
    "sport_organization_team_team_org_idx",
    (sportTeamId, sportOrganizationId),
    unique = true
  )
}

object SportOrganizationTeamTable extends WithTableQuery[SportOrganizationTeamTable] {
  val table: TableQuery[SportOrganizationTeamTable] =
    TableQuery[SportOrganizationTeamTable]
}