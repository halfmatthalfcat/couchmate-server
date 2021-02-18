package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.plainAPI._
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
  private[couchmate] val table: TableQuery[SportOrganizationTeamTable] =
    TableQuery[SportOrganizationTeamTable]

  private[couchmate] val insertOrGetSportOrganizationTeamIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_sport_organization_team_id(
              _sport_team_id BIGINT,
              _sport_organization_id BIGINT,
              OUT _sport_organization_team_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  sport_organization_team_id
                  FROM    sport_organization_team
                  WHERE   sport_team_id = _sport_team_id AND
                          sport_organization_id = _sport_organization_id
                  FOR     SHARE
                  INTO    _sport_organization_team_id;

                  EXIT WHEN FOUND;

                  INSERT INTO sport_organization_team
                  (sport_team_id, sport_organization_id)
                  VALUES
                  (_sport_team_id, _sport_organization_id)
                  ON CONFLICT (sport_team_id, sport_organization_id) DO NOTHING
                  RETURNING sport_organization_team_id
                  INTO _sport_organization_team_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}