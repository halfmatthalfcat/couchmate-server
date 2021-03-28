package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{SportOrganization, SportOrganizationTeam, SportTeam}
import com.couchmate.common.tables.SportOrganizationTeamTable
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object SportOrganizationTeamDAO {
  private[this] val getSportOrganizationTeamQuery = Compiled {
    (sportOrganizationTeamId: Rep[Long]) =>
      SportOrganizationTeamTable.table.filter(
        _.sportOrganizationTeamId === sportOrganizationTeamId
      )
  }

  def getSportOrganizationTeam(sportOrganizationTeamId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[SportOrganizationTeam]] = cache(
    "getSportOrganizationTeam",
    sportOrganizationTeamId
  )(db.run(getSportOrganizationTeamQuery(sportOrganizationTeamId).result.headOption))()

  private[this] val getSportOrganizationTeamForSportAndOrgQuery = Compiled {
    (sportTeamId: Rep[Long], sportOrganizationId: Rep[Long]) =>
      SportOrganizationTeamTable.table.filter { sOT =>
        sOT.sportTeamId === sportTeamId &&
        sOT.sportOrganizationId === sportOrganizationId
      }
  }

  def getSportOrganizationTeamForTeamAndOrg(
    sportTeamId: Long,
    sportOrganizationId: Long
  )(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[SportOrganizationTeam]] = cache(
    "getSportOrganizationTeamForTeamAndOrg",
    sportTeamId,
    sportOrganizationId
  )(db.run(getSportOrganizationTeamForSportAndOrgQuery(
    sportTeamId,
    sportOrganizationId
  ).result.headOption))(bust = bust)

  private[common] def getTeamAndOrgForOrgTeam(sportOrganizationTeamId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[(Option[SportTeam], Option[SportOrganization])] = cache(
    "getTeamAndOrgForOrgTeam",
    sportOrganizationTeamId
  )(for {
    sot <- getSportOrganizationTeam(sportOrganizationTeamId)
    st <- sot.fold(Future.successful(Option.empty[SportTeam]))(
      t => SportTeamDAO.getSportTeam(t.sportTeamId)
    )
    org <- sot.fold(Future.successful(Option.empty[SportOrganization]))(
      o => SportOrganizationDAO.getSportOrganization(o.sportOrganizationId)
    )
  } yield (st, org))()

  private[this] def addSportOrganizationTeamForId(
    sportTeamId: Long,
    sportOrganizationId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addSportOrganizationTeamForId",
    sportTeamId,
    sportOrganizationId
  )(db.run(
    sql"""SELECT insert_or_get_sport_organization_team_id(${sportTeamId}, ${sportOrganizationId})"""
      .as[Long].head
  ))()

  // I honestly have no idea why I need this but using getSportOrganizationTeamQuery
  // was returning sport_organization_id as both sport_organization_team_id _and_
  // sport_organization_id...no fucking clue
  private[this] def getSportOrganizationTeamRaw(sportOrganizationTeamId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[SportOrganizationTeam] = cache(
    "getSportOrganizationTeamRaw",
    sportOrganizationTeamId
  )(db.run(
    sql"""
          SELECT sport_organization_team_id, sport_team_id, sport_organization_id FROM sport_organization_team
          WHERE sport_organization_team_id = ${sportOrganizationTeamId}
         """.as[SportOrganizationTeam].head
  ))()

  private[common] def addAndGetSportOrganizationTeam(
    sportTeamId: Long,
    sportOrganizationId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[SportOrganizationTeam] = cache(
    "addAndGetSportOrganizationTeam",
    sportTeamId,
    sportOrganizationId
  )(for {
    exists <- getSportOrganizationTeamForTeamAndOrg(
      sportTeamId, sportOrganizationId
    )()
    sot <- exists.fold(for {
      _ <- addSportOrganizationTeamForId(sportTeamId, sportOrganizationId)
      selected <- getSportOrganizationTeamForTeamAndOrg(
        sportTeamId, sportOrganizationId
      )(bust = true).map(_.get)
    } yield selected)(Future.successful)
  } yield sot)()
}
