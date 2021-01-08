package com.couchmate.common.dao

import java.time.{LocalDateTime, ZoneId}

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{Airing, SportTeam}
import com.couchmate.common.tables.{AiringTable, ShowTable, SportEventTable, SportEventTeamTable, SportTeamTable}

import scala.concurrent.{ExecutionContext, Future}

trait SportTeamDAO {
  def getSportTeam(sportTeamId: Long)(
    implicit
    db: Database
  ): Future[Option[SportTeam]] =
    db.run(SportTeamDAO.getSportTeam(sportTeamId))

  def getSportTeamByExt(extSportTeamId: Long)(
    implicit
    db: Database
  ): Future[Option[SportTeam]] =
    db.run(SportTeamDAO.getSportTeamByExt(extSportTeamId))

  def upsertSportTeam(sportTeam: SportTeam)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[SportTeam] =
    db.run(SportTeamDAO.upsertSportTeam(sportTeam))
}

object SportTeamDAO {
  private[this] lazy val getSportTeamQuery = Compiled {
    (sportTeamId: Rep[Long]) =>
      SportTeamTable.table.filter(_.sportTeamId === sportTeamId)
  }

  private[common] def getSportTeam(sportTeamId: Long): DBIO[Option[SportTeam]] =
    getSportTeamQuery(sportTeamId).result.headOption

  private[this] lazy val getSportTeamByExtQuery = Compiled {
    (extSportTeamId: Rep[Long]) =>
      SportTeamTable.table.filter(_.extSportTeamId === extSportTeamId)
  }

  private[common] def getSportTeamByExt(extSportTeamId: Long): DBIO[Option[SportTeam]] =
    getSportTeamByExtQuery(extSportTeamId).result.headOption

  private[common] def getUpcomingSportTeamAirings(sportTeamId: Long): DBIO[Seq[Airing]] = (for {
    sET <- SportEventTeamTable.table if sET.sportTeamId === sportTeamId
    s <- ShowTable.table if s.sportEventId === sET.sportEventId
    a <- AiringTable.table if (
      a.showId === s.showId &&
      a.startTime >= LocalDateTime.now(ZoneId.of("UTC"))
    )
  } yield a).result

  private[common] def upsertSportTeam(sportTeam: SportTeam): DBIO[SportTeam] =
    (SportTeamTable.table returning SportTeamTable.table) += sportTeam

  private[common] def addOrGetSportTeam(st: SportTeam) =
    sql"""
         WITH input_rows(ext_sport_team_id, name) as (
          VALUES(${st.extSportTeamId}, ${st.name})
         ), ins AS (
          INSERT INTO sport_team (ext_sport_team_id, name)
          SELECT * FROM input_rows
          ON CONFLICT (ext_sport_team_id) DO NOTHING
          RETURNING sport_team_id, ext_sport_team_id, name
         ), sel AS (
          SELECT sport_team_id, ext_sport_team_id, name
          FROM ins
          UNION ALL
          SELECT st.sport_team_id, ext_sport_team_id, name
          FROM input_rows
          JOIN sport_team AS st USING (ext_sport_team_id, name)
         ), ups AS (
          INSERT INTO sport_team AS st (ext_sport_team_id, name)
          SELECT  i.*
          FROM    input_rows  i
          LEFT    JOIN sel    s USING (ext_sport_team_id, name)
          WHERE   s.sport_team_id IS NULL
          ON      CONFLICT (ext_sport_team_id) DO UPDATE
          SET     name = excluded.name
          RETURNING sport_team_id, ext_sport_team_id, name
         )  SELECT  sport_team_id, ext_sport_team_id, name FROM sel
            UNION   ALL
            TABLE   ups;
         """.as[SportTeam]
}