package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.grid.GridSportTeam
import com.couchmate.common.models.data.SportEventTeam
import com.couchmate.common.tables.{SportEventTeamTable, SportTeamTable}

import scala.concurrent.{ExecutionContext, Future}

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
    (sportTeamId: Rep[Long]) =>
      SportEventTeamTable.table.filter(_.sportTeamId === sportTeamId)
  }

  private[common] def getEventsForSportsTeam(sportTeamId: Long): DBIO[Seq[SportEventTeam]] =
    getEventsForSportsTeamQuery(sportTeamId).result

  private[this] lazy val getSportEventTeamQuery = Compiled {
    (sportEventId: Rep[Long], sportTeamId: Rep[Long]) =>
      SportEventTeamTable.table.filter { sEV =>
        sEV.sportEventId === sportEventId &&
        sEV.sportTeamId === sportTeamId
      }
  }

  private[common] def getSportEventTeam(
    sportEventId: Long,
    sportTeamId: Long
  ): DBIO[Option[SportEventTeam]] =
    getSportEventTeamQuery(sportEventId, sportTeamId).result.headOption

  private[common] def getGridSportTeamsForEvent(sportEventId: Long)(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[GridSportTeam]] = for {
    event <- SportEventTeamTable.table.filter(_.sportEventId === sportEventId).result
    teams <- DBIO.sequence(
      event.map(e =>
        SportTeamDAO.getSportTeam(e.sportTeamId).map(_.map(st => GridSportTeam(
          st.sportTeamId.get,
          st.name,
          e.isHome
        )))
      )
    )
  } yield teams.filter(_.nonEmpty).map(_.get)

  private[common] def upsertSportEventTeam(sportEventTeam: SportEventTeam)(
    implicit
    ec: ExecutionContext
  ): DBIO[SportEventTeam] = for {
    exists <- getSportEventTeam(
      sportEventTeam.sportEventId,
      sportEventTeam.sportTeamId
    )
    sEV <- exists.fold[DBIO[SportEventTeam]](
      (SportEventTeamTable.table returning SportEventTeamTable.table) += sportEventTeam
    )(_ => for {
      _ <- SportEventTeamTable
        .table
        .filter { sEV =>
          sEV.sportEventId === sportEventTeam.sportEventId &&
          sEV.sportTeamId === sportEventTeam.sportTeamId
        }.update(sportEventTeam)
      updated <- getSportEventTeam(
        sportEventTeam.sportEventId,
        sportEventTeam.sportTeamId
      )
    } yield updated.get)
  } yield sEV

  private[common] def getOrAddSportEventTeam(sET: SportEventTeam) =
    sql"""
         WITH input_rows(sport_event_id, sport_team_id, is_home) as (
          VALUES(${sET.sportEventId}, ${sET.sportTeamId}, ${sET.isHome})
         ), ins AS (
          INSERT INTO sport_event_team (sport_event_id, sport_team_id, is_home)
          SELECT * FROM input_rows
          ON CONFLICT (sport_event_id, sport_team_id) DO NOTHING
          RETURNING sport_event_id, sport_team_id, is_home
         ), sel AS (
          SELECT sport_event_id, sport_team_id, is_home
          FROM ins
          UNION ALL
          SELECT sport_event_id, sport_team_id, set.is_home
          FROM input_rows
          JOIN sport_event_team as set USING (sport_event_id, sport_team_id)
         ), ups AS (
          INSERT INTO sport_event_team AS set (sport_event_id, sport_team_id, is_home)
          SELECT  i.*
          FROM    input_rows  i
          LEFT    JOIN sel    s USING (sport_event_id, sport_team_id)
          WHERE   s.sport_team_id IS NULL AND
                  s.sport_event_id IS NULL
          ON      CONFLICT (sport_event_id, sport_team_id) DO UPDATE
          SET     is_home = set.is_home
          RETURNING sport_event_id, sport_team_id, is_home
         )  SELECT   sport_event_id, sport_team_id, is_home FROM sel
            UNION    ALL
            TABLE    ups;
         """.as[SportEventTeam]
}