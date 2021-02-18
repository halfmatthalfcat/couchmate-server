package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{SportOrganization, SportOrganizationTeam, SportTeam}
import com.couchmate.common.tables.SportOrganizationTeamTable

import scala.concurrent.{ExecutionContext, Future}

trait SportOrganizationTeamDAO {
  def getSportOrganizationTeam(sportOrganizationTeamId: Long)(
    implicit
    db: Database
  ): Future[Option[SportOrganizationTeam]] =
    db.run(SportOrganizationTeamDAO.getSportOrganizationTeam(sportOrganizationTeamId))

  def getSportOrganizationTeamForTeamAndOrg(
    sportTeamId: Long,
    sportOrganizationId: Long
  )(implicit db: Database): Future[Option[SportOrganizationTeam]] =
    db.run(SportOrganizationTeamDAO.getSportOrganizationTeamForSportAndOrg(
      sportTeamId, sportOrganizationId
    ))

  def upsertSportOrganizationTeam(
    sportTeamId: Long,
    sportOrganizationId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[SportOrganizationTeam] =
    db.run(SportOrganizationTeamDAO.upsertSportOrganizationTeam(
      sportTeamId, sportOrganizationId
    ))
}

object SportOrganizationTeamDAO {
  private[this] val getSportOrganizationTeamQuery = Compiled {
    (sportOrganizationTeamId: Rep[Long]) =>
      SportOrganizationTeamTable.table.filter(
        _.sportOrganizationTeamId === sportOrganizationTeamId
      )
  }

  private[common] def getSportOrganizationTeam(
    sportOrganizationTeamId: Long
  ): DBIO[Option[SportOrganizationTeam]] =
    getSportOrganizationTeamQuery(sportOrganizationTeamId).result.headOption

  private[this] val getSportOrganizationTeamForSportAndOrgQuery = Compiled {
    (sportTeamId: Rep[Long], sportOrganizationId: Rep[Long]) =>
      SportOrganizationTeamTable.table.filter { sOT =>
        sOT.sportTeamId === sportTeamId &&
        sOT.sportOrganizationId === sportOrganizationId
      }
  }

  private[common] def getSportOrganizationTeamForSportAndOrg(
    sportTeamId: Long,
    sportOrganizationId: Long
  ): DBIO[Option[SportOrganizationTeam]] =
    getSportOrganizationTeamForSportAndOrgQuery(sportTeamId, sportOrganizationId).result.headOption

  private[common] def getTeamAndOrgForOrgTeam(sportOrganizationTeamId: Long)(
    implicit
    ec: ExecutionContext
  ): DBIO[(Option[SportTeam], Option[SportOrganization])] = for {
    sot <- getSportOrganizationTeam(sportOrganizationTeamId)
    st <- sot.fold[DBIO[Option[SportTeam]]](DBIO.successful(Option.empty))(
      t => SportTeamDAO.getSportTeam(t.sportTeamId)
    )
    org <- sot.fold[DBIO[Option[SportOrganization]]](DBIO.successful(Option.empty))(
      o => SportOrganizationDAO.getSportOrganization(o.sportOrganizationId)
    )
  } yield (st, org)

  private[common] def upsertSportOrganizationTeam(
    sportTeamId: Long,
    sportOrganizationId: Long
  )(implicit ec: ExecutionContext): DBIO[SportOrganizationTeam] = for {
    exists <- getSportOrganizationTeamForSportAndOrg(
      sportTeamId, sportOrganizationId
    )
    sportOrganizationTeam <- exists.fold[DBIO[SportOrganizationTeam]](
      (SportOrganizationTeamTable.table returning SportOrganizationTeamTable.table) += SportOrganizationTeam(
        None, sportTeamId, sportOrganizationId
      )
    )(DBIO.successful)
  } yield sportOrganizationTeam

  private[this] def addSportOrganizationTeamForId(
    sportTeamId: Long,
    sportOrganizationId: Long
  ) =
    sql"""
        WITH row AS (
          INSERT INTO sport_organization_team
          (sport_team_id, sport_organization_id)
          VALUES
          (${sportTeamId}, ${sportOrganizationId})
          ON CONFLICT (sport_team_id, sport_organization_id)
          DO NOTHING
          RETURNING sport_organization_team_id
        ) SELECT sport_organization_team_id FROM row
          UNION SELECT sport_organization_team_id FROM sport_organization_team
          WHERE sport_team_id = ${sportTeamId} AND
                sport_organization_id = ${sportOrganizationId}
      """.as[Long]

  // I honestly have no idea why I need this but using getSportOrganizationTeamQuery
  // was returning sport_organization_id as both sport_organization_team_id _and_
  // sport_organization_id...no fucking clue
  private[this] def getSportOrganizationTeamRaw(sportOrganizationTeamId: Long) =
    sql"""
          SELECT sport_organization_team_id, sport_team_id, sport_organization_id FROM sport_organization_team
          WHERE sport_organization_team_id = ${sportOrganizationTeamId}
         """.as[SportOrganizationTeam]

  private[common] def addAndGetSportOrganizationTeam(
    sportTeamId: Long,
    sportOrganizationId: Long
  )(implicit ec: ExecutionContext): DBIO[SportOrganizationTeam] = (for {
    sportOrganizationTeamId <- addSportOrganizationTeamForId(
      sportTeamId, sportOrganizationId
    ).head
    sportOrganizationTeam <- getSportOrganizationTeamRaw(sportOrganizationTeamId).head
  } yield sportOrganizationTeam).transactionally

  private[common] def addOrGetSportOrganizationTeam(
    sportTeamId: Long,
    sportOrganizationId: Long
  ) = sql"""
     WITH input_rows(sport_team_id, sport_organization_id) AS (
      VALUES(${sportTeamId}, ${sportOrganizationId})
     ), ins AS (
      INSERT INTO sport_organization_team (sport_team_id, sport_organization_id)
      SELECT * FROM input_rows
      ON CONFLICT (sport_team_id, sport_organization_id) DO NOTHING
      RETURNING sport_organization_team_id, sport_team_id, sport_organization_id
     ), sel AS (
      SELECT sport_organization_team_id, sport_team_id, sport_organization_id
      FROM ins
      UNION ALL
      SELECT sot.sport_organization_team_id, sport_team_id, sport_organization_id
      FROM input_rows
      JOIN sport_organization_team AS sot USING (sport_team_id, sport_organization_id)
     ), ups AS (
      INSERT INTO sport_organization_team AS sot (sport_team_id, sport_organization_id)
      SELECT  i.*
      FROM    input_rows  i
      LEFT    JOIN sel    s USING (sport_team_id, sport_organization_id)
      WHERE   s.sport_team_id IS NULL
      AND     s.sport_organization_id IS NULL
      ON CONFLICT (sport_team_id, sport_organization_id) DO UPDATE
      SET     sport_team_id = sot.sport_team_id,
             sport_organization_id = sot.sport_organization_id
      RETURNING sport_organization_team_id, sport_team_id, sport_organization_id
     )  SELECT sport_organization_team_id, sport_team_id, sport_organization_id FROM sel
        UNION ALL
        TABLE ups;
     """.as[SportOrganizationTeam]
}
