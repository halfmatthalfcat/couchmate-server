package com.couchmate.common.dao

import java.time.{LocalDateTime, ZoneId}
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.api.grid.{GridAiring, GridSport, GridSportRow}
import com.couchmate.common.models.data.{Airing, SportEventTeam, SportOrganization, SportTeam}
import com.couchmate.common.tables.{AiringTable, LineupTable, ProviderChannelTable, ProviderTable, ShowTable, SportEventTable, SportEventTeamTable, SportTeamTable, UserNotificationTeamTable}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache
import slick.sql.SqlStreamingAction

import scala.concurrent.{ExecutionContext, Future}

object SportTeamDAO {
  private[this] lazy val getSportTeamQuery = Compiled {
    (sportTeamId: Rep[Long]) =>
      SportTeamTable.table.filter(_.sportTeamId === sportTeamId)
  }

  def getSportTeam(sportTeamId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[SportTeam]] = cache(
    "getSportTeam",
    sportTeamId
  )(db.run(getSportTeamQuery(sportTeamId).result.headOption))()

  private[this] lazy val getSportTeamByExtQuery = Compiled {
    (extSportTeamId: Rep[Long]) =>
      SportTeamTable.table.filter(_.extSportTeamId === extSportTeamId)
  }

  def getSportTeamByExt(extSportTeamId: Long)(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[SportTeam]] = cache(
    "getSportTeamByExt",
    extSportTeamId
  )(db.run(getSportTeamByExtQuery(extSportTeamId).result.headOption))(
    bust = bust
  )

  def getUpcomingSportTeamAirings(
    sportOrganizationTeamId: Long,
    providerId: Long
  )(implicit db: Database): Future[Seq[Airing]] = db.run((for {
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
  } yield a).result)

  private[common] def getGridSport(
    sportEventId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[GridSport]] = cache(
    "getGridSport",
    sportEventId
  )(for {
    sport <- SportEventDAO.getSportEvent(sportEventId)()
    org <- sport.fold(Future.successful(Option.empty[SportOrganization]))(
      s => s.sportOrganizationId.fold(
        Future.successful(Option.empty[SportOrganization])
      )(orgId => SportOrganizationDAO.getSportOrganization(orgId))
    )
    teams <- sport.fold(Future.successful(Seq.empty[SportEventTeam]))(
      _ => SportEventTeamDAO.getSportEventTeams(sportEventId)
    )
    gridTeams <- Future.sequence(teams.map(team => getGridSportRow(
      sportEventId,
      team.sportOrganizationTeamId
    )))
  } yield sport.fold[Option[GridSport]](Option.empty)(
    s => Some(GridSport(
      sportEventId,
      s.sportEventTitle,
      org.map(_.sportName).getOrElse("Unknown"),
      org.flatMap(_.orgName),
      gridTeams.collect {
        case Some(value) => value.toGridSportTeam
      }
    ))
  ))()

  def getAllGridSportRows(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[GridSportRow]] = cache(
    "getAllGridSportRows"
  )(db.run(
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
         """.as[GridSportRow]
  ))()

  def getSomeGridSportRows(sportEvents: Seq[Long])(
    implicit
    db: Database,
  ): Future[Seq[GridSportRow]] = db.run(
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
          WHERE   se.sport_event_id = any($sportEvents)
         """.as[GridSportRow]
  )

  def getGridSportRow(
    sportEventId: Long,
    sportOrganizationTeamId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[GridSportRow]] =cache(
    "getGridSportRow",
    sportEventId,
    sportOrganizationTeamId
  )(db.run(
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
       """.as[GridSportRow].headOption
  ))()

  private[this] def addSportTeamForId(st: SportTeam)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addSportTeamForId",
    st.extSportTeamId
  )(db.run(
    sql"""SELECT insert_or_get_sport_team_id(${st.extSportTeamId}, ${st.name})"""
      .as[Long].head
  ))()

  private[common] def addOrGetSportTeam(st: SportTeam)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[SportTeam] = cache(
    "addOrGetSportTeam",
    st.extSportTeamId
  )(for {
    exists <- getSportTeamByExt(st.extSportTeamId)()
    s <- exists.fold(for {
      _ <- addSportTeamForId(st)
      selected <- getSportTeamByExt(st.extSportTeamId)(bust = true).map(_.get)
    } yield selected)(Future.successful)
  } yield s)()
}