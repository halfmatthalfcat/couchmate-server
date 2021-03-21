package com.couchmate.common.dao

import java.time.{LocalDateTime, ZoneId}

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.api.grid.{GridAiring, GridSport, GridSportRow}
import com.couchmate.common.models.data.{Airing, SportEventTeam, SportOrganization, SportTeam}
import com.couchmate.common.tables.{AiringTable, LineupTable, ProviderChannelTable, ProviderTable, ShowTable, SportEventTable, SportEventTeamTable, SportTeamTable, UserNotificationTeamTable}
import slick.sql.SqlStreamingAction

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

  def getGridSportTeam(
    sportEventId: Long,
    sportTeamId: Long
  )(implicit db: Database): Future[Option[GridSportRow]] =
    db.run(SportTeamDAO.getGridSportTeam(sportEventId, sportTeamId).headOption)

  def getAllGridSportRows(
    implicit
    db: Database
  ): Future[Seq[GridSportRow]] =
    db.run(SportTeamDAO.getAllGridSportRows)

  def getGridSport(sportEventId: Long)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Option[GridSport]] =
    db.run(SportTeamDAO.getGridSport(sportEventId))
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

  private[common] def getUpcomingSportTeamAirings(
    sportOrganizationTeamId: Long,
    providerId: Long
  ): DBIO[Seq[Airing]] = (for {
    sET <- SportEventTeamTable.table if sET.sportOrganizationTeamId === sportOrganizationTeamId
    s <- ShowTable.table if s.sportEventId === sET.sportEventId
    a <- AiringTable.table if (
      a.showId === s.showId &&
      a.startTime >= LocalDateTime.now(ZoneId.of("UTC"))
    )
    l <- LineupTable.table if a.airingId === l.airingId
    pc <- ProviderChannelTable.table if (
      pc.providerChannelId === l.providerChannelId &&
      pc.providerId === providerId
    )
  } yield a).result

  private[common] def upsertSportTeam(sportTeam: SportTeam): DBIO[SportTeam] =
    (SportTeamTable.table returning SportTeamTable.table) += sportTeam

  private[common] def getGridSport(
    sportEventId: Long
  )(implicit ec: ExecutionContext): DBIO[Option[GridSport]] = for {
    sport <- SportEventDAO.getSportEvent(sportEventId)
    org <- sport.fold[DBIO[Option[SportOrganization]]](DBIO.successful(Option.empty))(
      s => s.sportOrganizationId.fold[DBIO[Option[SportOrganization]]](
        DBIO.successful(Option.empty)
      )(orgId => SportOrganizationDAO.getSportOrganization(orgId))
    )
    teams <- sport.fold[DBIO[Seq[SportEventTeam]]](DBIO.successful(Seq.empty))(
      _ => SportEventTeamDAO.getSportEventTeams(sportEventId)
    )
    gridTeams <- DBIO.fold(
      teams.map(team => getGridSportTeam(
        sportEventId,
        team.sportOrganizationTeamId
      )),
      Seq.empty
    )(_ ++ _)
  } yield sport.fold[Option[GridSport]](Option.empty)(
    s => Some(GridSport(
      sportEventId,
      s.sportEventTitle,
      org.map(_.sportName).getOrElse("Unknown"),
      org.flatMap(_.orgName),
      gridTeams.map(_.toGridSportTeam)
    ))
  )

  private[common] def getAllGridSportRows: SqlStreamingAction[Seq[GridSportRow], GridSportRow, Effect] =
    sql"""
          SELECT  set.sport_event_id,
                  sot.sport_organization_team_id,
                  se.sport_event_title,
                  so.sport_name,
                  so.org_name,
                  st.name,
                  set.is_home,
                  coalesce(notifications.count, 0) as follows
          FROM    sport_organization_team as sot
          JOIN    sport_team as st
          ON      st.sport_team_id = sot.sport_team_id
          JOIN    sport_organization as so
          ON      sot.sport_organization_id = so.sport_organization_id
          JOIN    sport_event_team as set
          ON      set.sport_organization_team_id = sot.sport_organization_team_id
          JOIN    sport_event as se
          ON      se.sport_event_id = set.sport_event_id
          LEFT JOIN (
              SELECT  team_id, count(*) as count
              FROM    user_notification_team
              GROUP BY team_id
          ) as notifications
          ON      notifications.team_id = sot.sport_organization_team_id
          ORDER BY sport_event_id DESC
         """.as[GridSportRow]

  private[common] def getGridSportTeam(
    sportEventId: Long,
    sportOrganizationTeamId: Long
  ): SqlStreamingAction[Seq[GridSportRow], GridSportRow, Effect] =
    sql"""SELECT  set.sport_event_id, sot.sport_organization_team_id, st.name, set.is_home, (
            SELECT  count(*)
            FROM    user_notification_team
            WHERE   team_id = ${sportOrganizationTeamId}
          ) as follows
          FROM    sport_organization_team as sot
          JOIN    sport_team as st
          ON      st.sport_team_id = sot.sport_team_id
          JOIN    sport_event_team as set
          ON      set.sport_organization_team_id = sot.sport_organization_team_id
          AND     set.sport_event_id = ${sportEventId}
          WHERE   sot.sport_organization_team_id = ${sportOrganizationTeamId}
       """.as[GridSportRow]

  private[this] def addSportTeamForId(st: SportTeam) =
    sql"""SELECT insert_or_get_sport_team_id(${st.extSportTeamId}, ${st.name})""".as[Long]

  private[common] def addAndGetSportTeam(st: SportTeam)(
    implicit
    ec: ExecutionContext
  ): DBIO[SportTeam] = (for {
    sportTeamId <- addSportTeamForId(st).head
    sportTeam <- getSportTeamQuery(sportTeamId).result.head
  } yield sportTeam)

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