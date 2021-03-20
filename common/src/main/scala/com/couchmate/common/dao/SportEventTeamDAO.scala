package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.grid.GridSportTeam
import com.couchmate.common.models.data.SportEventTeam
import com.couchmate.common.tables.{SportEventTeamTable, SportTeamTable}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait SportEventTeamDAO {

  def getSportEventTeams(sportEventId: Long)(
    implicit
    db: Database
  ): Future[Seq[SportEventTeam]] =
    db.run(SportEventTeamDAO.getSportEventTeams(sportEventId))

  def getEventsForSportsTeam(sportTeamId: Long)(
    implicit
    db: Database
  ): Future[Seq[SportEventTeam]] =
    db.run(SportEventTeamDAO.getEventsForSportsTeam(sportTeamId))

  def getSportEventTeam(
    sportEventId: Long,
    sportTeamId: Long
  )(
    implicit
    db: Database
  ): Future[Option[SportEventTeam]] =
    db.run(SportEventTeamDAO.getSportEventTeam(
      sportEventId,
      sportTeamId
    ))

  def upsertSportEventTeam(sportEvent: SportEventTeam)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[SportEventTeam] =
    db.run(SportEventTeamDAO.upsertSportEventTeam(sportEvent))
}

object SportEventTeamDAO {
  private[this] lazy val getSportEventTeamsQuery = Compiled {
    (sportEventId: Rep[Long]) =>
      SportEventTeamTable.table.filter(_.sportEventId === sportEventId)
  }

  private[common] def getSportEventTeams(sportEventId: Long): DBIO[Seq[SportEventTeam]] =
    getSportEventTeamsQuery(sportEventId).result

  private[this] lazy val getEventsForSportsTeamQuery = Compiled {
    (sportOrganizationTeamId: Rep[Long]) =>
      SportEventTeamTable.table.filter(_.sportOrganizationTeamId === sportOrganizationTeamId)
  }

  private[common] def getEventsForSportsTeam(sportOrganizationTeamId: Long): DBIO[Seq[SportEventTeam]] =
    getEventsForSportsTeamQuery(sportOrganizationTeamId).result

  private[this] lazy val getSportEventTeamQuery = Compiled {
    (sportEventId: Rep[Long], sportOrganizationTeamId: Rep[Long]) =>
      SportEventTeamTable.table.filter { sEV =>
        sEV.sportEventId === sportEventId &&
        sEV.sportOrganizationTeamId === sportOrganizationTeamId
      }
  }

  private[common] def getSportEventTeam(
    sportEventId: Long,
    sportOrganizationTeamId: Long
  ): DBIO[Option[SportEventTeam]] =
    getSportEventTeamQuery(sportEventId, sportOrganizationTeamId).result.headOption

  private[common] def upsertSportEventTeam(sportEventTeam: SportEventTeam)(
    implicit
    ec: ExecutionContext
  ): DBIO[SportEventTeam] = for {
    exists <- getSportEventTeam(
      sportEventTeam.sportEventId,
      sportEventTeam.sportOrganizationTeamId
    )
    sEV <- exists.fold[DBIO[SportEventTeam]](
      (SportEventTeamTable.table returning SportEventTeamTable.table) += sportEventTeam
    )(_ => for {
      _ <- SportEventTeamTable
        .table
        .filter { sEV =>
          sEV.sportEventId === sportEventTeam.sportEventId &&
          sEV.sportOrganizationTeamId === sportEventTeam.sportOrganizationTeamId
        }.update(sportEventTeam)
      updated <- getSportEventTeam(
        sportEventTeam.sportEventId,
        sportEventTeam.sportOrganizationTeamId
      )
    } yield updated.get)
  } yield sEV

  private[this] def addSportEventTeamForId(sET: SportEventTeam) =
    sql"""SELECT (insert_or_get_sport_event_team_id(${sET.sportEventId}, ${sET.sportOrganizationTeamId}, ${sET.isHome})).*""".as[(Long, Long)]

  private[common] def addAndGetSportEventTeam(sET: SportEventTeam)(
    implicit
    ec: ExecutionContext
  ): DBIO[SportEventTeam] = (for {
    (sportEventId, sportOrganizationTeamId) <- addSportEventTeamForId(sET).head
    sportEventTeam <- getSportEventTeamQuery(sportEventId, sportOrganizationTeamId).result.head
  } yield sportEventTeam)

  private[common] def getOrAddSportEventTeam(sET: SportEventTeam) =
    sql"""
         WITH input_rows(sport_event_id, sport_organization_team_id, is_home) as (
          VALUES(${sET.sportEventId}, ${sET.sportOrganizationTeamId}, ${sET.isHome})
         ), ins AS (
          INSERT INTO sport_event_team (sport_event_id, sport_organization_team_id, is_home)
          SELECT * FROM input_rows
          ON CONFLICT (sport_event_id, sport_organization_team_id) DO NOTHING
          RETURNING sport_event_id, sport_organization_team_id, is_home
         ), sel AS (
          SELECT sport_event_id, sport_organization_team_id, is_home
          FROM ins
          UNION ALL
          SELECT sport_event_id, sport_organization_team_id, set.is_home
          FROM input_rows
          JOIN sport_event_team as set USING (sport_event_id, sport_organization_team_id)
         ), ups AS (
          INSERT INTO sport_event_team AS set (sport_event_id, sport_organization_team_id, is_home)
          SELECT  i.*
          FROM    input_rows  i
          LEFT    JOIN sel    s USING (sport_event_id, sport_organization_team_id)
          WHERE   s.sport_organization_team_id IS NULL AND
                  s.sport_event_id IS NULL
          ON      CONFLICT (sport_event_id, sport_organization_team_id) DO UPDATE
          SET     is_home = set.is_home
          RETURNING sport_event_id, sport_organization_team_id, is_home
         )  SELECT   sport_event_id, sport_organization_team_id, is_home FROM sel
            UNION    ALL
            TABLE    ups;
         """.as[SportEventTeam]
}