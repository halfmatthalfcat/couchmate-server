package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.SportEventTeam
import com.couchmate.common.util.slick.WithTableQuery

class SportEventTeamTable(tag: Tag) extends Table[SportEventTeam](tag, "sport_event_team") {
  def sportEventId: Rep[Long] = column[Long]("sport_event_id")
  def sportOrganizationTeamId: Rep[Long] = column[Long]("sport_organization_team_id")
  def isHome: Rep[Boolean] = column[Boolean]("is_home")

  def * = (
    sportEventId,
    sportOrganizationTeamId,
    isHome
  ) <> ((SportEventTeam.apply _).tupled, SportEventTeam.unapply)

  def sportEventTeamTablePk = primaryKey(
    "sport_event_team_pk",
    (sportEventId, sportOrganizationTeamId)
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

  def sportOrgTeamFk = foreignKey(
    "sport_event_team_sport_organization_team_fk",
    sportOrganizationTeamId,
    SportOrganizationTeamTable.table
  )(
    _.sportOrganizationTeamId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  // deprecated
  def sportTeamId: Rep[Long] = column("sport_team_id")
  def sportEventTeamTablePkOld = primaryKey(
    "sport_event_team_pk",
    (sportEventId, sportTeamId)
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

  private[couchmate] val insertOrGetSportEventTeamIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_sport_event_team_id(
              _sport_event_id BIGINT,
              _sport_organization_team_id BIGINT,
              _is_home BOOL,
              OUT __sport_event_id BIGINT,
              OUT __sport_organization_team_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  sport_event_id, sport_organization_team_id
                  FROM    sport_event_team
                  WHERE   sport_event_id = _sport_event_id AND
                          sport_organization_team_id = _sport_organization_team_id
                  FOR     SHARE
                  INTO    __sport_event_id, __sport_organization_team_id;

                  EXIT WHEN FOUND;

                  INSERT INTO sport_event_team
                  (sport_event_id, is_home, sport_organization_team_id)
                  VALUES
                  (_sport_event_id, _is_home, _sport_organization_team_id)
                  ON CONFLICT (sport_event_id, sport_organization_team_id) DO NOTHING
                  RETURNING sport_event_id, sport_organization_team_id
                  INTO __sport_event_id, __sport_organization_team_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
