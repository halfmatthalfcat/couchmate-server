package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.SportEventTeam
import com.couchmate.common.tables.{SportEventTeamTable, SportTeamTable}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object SportEventTeamDAO {
  private[this] lazy val getSportEventTeamsQuery = Compiled {
    (sportEventId: Rep[Long]) =>
      SportEventTeamTable.table.filter(_.sportEventId === sportEventId)
  }

  def getSportEventTeams(sportEventId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[SportEventTeam]] = cache(
    "getSportEventTeams",
    sportEventId
  )(db.run(getSportEventTeamsQuery(sportEventId).result))()

  private[this] lazy val getEventsForSportsTeamQuery = Compiled {
    (sportOrganizationTeamId: Rep[Long]) =>
      SportEventTeamTable.table.filter(_.sportOrganizationTeamId === sportOrganizationTeamId)
  }

  def getEventsForSportsTeam(sportTeamId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[SportEventTeam]] = cache(
    "getEventsForSportsTeam",
    sportTeamId
  )(db.run(getEventsForSportsTeamQuery(sportTeamId).result))()

  private[this] lazy val getSportEventTeamQuery = Compiled {
    (sportEventId: Rep[Long], sportOrganizationTeamId: Rep[Long]) =>
      SportEventTeamTable.table.filter { sEV =>
        sEV.sportEventId === sportEventId &&
        sEV.sportOrganizationTeamId === sportOrganizationTeamId
      }
  }

  def getSportEventTeam(
    sportEventId: Long,
    sportTeamId: Long
  )(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[SportEventTeam]] = cache(
    "getSportEventTeam",
    sportEventId,
    sportTeamId
  )(db.run(getSportEventTeamQuery(
    sportEventId,
    sportTeamId
  ).result.headOption))(bust = bust)

  private[this] def addSportEventTeamForId(sET: SportEventTeam)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[(Long, Long)] = cache(
    "addSportEventTeamForId",
    sET.sportEventId,
    sET.sportOrganizationTeamId,
    sET.isHome
  )(db.run(
    sql"""SELECT (insert_or_get_sport_event_team_id(${sET.sportEventId}, ${sET.sportOrganizationTeamId}, ${sET.isHome})).*"""
      .as[(Long, Long)].head
  ))()

  private[common] def addOrGetSportEventTeam(sET: SportEventTeam)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[SportEventTeam] = cache(
    "addOrGetSportEventTeam",
    sET.sportEventId,
    sET.sportOrganizationTeamId,
    sET.isHome
  )(for {
    exists <- getSportEventTeam(
      sET.sportEventId,
      sET.sportOrganizationTeamId
    )()
    s <- exists.fold(for {
      _ <- addSportEventTeamForId(sET)
      selected <- getSportEventTeam(
        sET.sportEventId,
        sET.sportOrganizationTeamId
      )(bust = true).map(_.get)
    } yield selected)(Future.successful)
  } yield s)()

}