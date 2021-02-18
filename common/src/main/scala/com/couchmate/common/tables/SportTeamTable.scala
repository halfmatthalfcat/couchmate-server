package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.SportTeam
import com.couchmate.common.util.slick.WithTableQuery

class SportTeamTable(tag: Tag) extends Table[SportTeam](tag, "sport_team") {
  def sportTeamId: Rep[Long] = column[Long]("sport_team_id", O.PrimaryKey, O.AutoInc)
  def extSportTeamId: Rep[Long] = column[Long]("ext_sport_team_id", O.Unique)
  def name: Rep[String] = column[String]("name")

  def * = (
    sportTeamId.?,
    extSportTeamId,
    name
  ) <> ((SportTeam.apply _).tupled, SportTeam.unapply)

  def uniqueExtId = index(
    "ext_sport_team_idx",
    extSportTeamId,
    unique = true
  )
}

object SportTeamTable extends WithTableQuery[SportTeamTable] {
  private[couchmate] val table: TableQuery[SportTeamTable] =
    TableQuery[SportTeamTable]

  private[couchmate] val insertOrGetSportTeamIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_sport_team_id(
              _ext_sport_team_id BIGINT,
              _name VARCHAR,
              OUT _sport_team_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  sport_team_id
                  FROM    sport_team
                  WHERE   ext_sport_team_id = _ext_sport_team_id
                  FOR     SHARE
                  INTO    _sport_team_id;

                  EXIT WHEN FOUND;

                  INSERT INTO sport_team
                  (ext_sport_team_id, name)
                  VALUES
                  (_ext_sport_team_id, _name)
                  ON CONFLICT (ext_sport_team_id) DO NOTHING
                  RETURNING sport_team_id
                  INTO _sport_team_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}