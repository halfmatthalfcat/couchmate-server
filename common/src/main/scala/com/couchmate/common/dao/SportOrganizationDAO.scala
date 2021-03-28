package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.SportOrganization
import com.couchmate.common.tables.SportOrganizationTable
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object SportOrganizationDAO {
  private[this] lazy val getSportOrganizationQuery = Compiled { (sportOrganizationId: Rep[Long]) =>
    SportOrganizationTable.table.filter(_.sportOrganizationId === sportOrganizationId)
  }

  def getSportOrganization(sportOrganizationId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[SportOrganization]] = cache(
    "getSportOrganization",
    sportOrganizationId
  )(db.run(getSportOrganizationQuery(sportOrganizationId).result.headOption))()

  private[this] lazy val getSportOrganizationBySportAndOrgQuery = Compiled {
    (extSportId: Rep[Long], extOrgId: Rep[Option[Long]]) =>
      SportOrganizationTable.table.filter { so =>
        so.extSportId === extSportId &&
        so.extOrgId === extOrgId
      }
  }

  def getSportOrganizationBySportAndOrg(extSportId: Long, extOrgId: Option[Long])(
    bust: Boolean = false
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[SportOrganization]] = cache(
    "getSportOrganizationBySportAndOrg",
    extSportId,
    extOrgId.getOrElse(0L)
  )(db.run(getSportOrganizationBySportAndOrgQuery(
    extSportId,
    extOrgId
  ).result.headOption))(bust = bust)

  private[this] def addSportOrganizationForId(so: SportOrganization)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addSportOrganizationForId",
    so.extSportId,
    so.extOrgId.getOrElse(0L)
  )(db.run(
    sql"""SELECT insert_or_get_sport_organization_id(${so.extSportId}, ${so.extOrgId}, ${so.sportName}, ${so.orgName})"""
      .as[Long].head
  ))()

  private[common] def addOrGetSportOrganization(so: SportOrganization)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[SportOrganization] = cache(
    "addOrGetSportOrganization",
    so.extSportId,
    so.extOrgId.getOrElse(0L)
  )(for {
    exists <- getSportOrganizationBySportAndOrg(
      so.extSportId,
      so.extOrgId
    )()
    s <- exists.fold(for {
      _ <- addSportOrganizationForId(so)
      selected <- getSportOrganizationBySportAndOrg(
        so.extSportId,
        so.extOrgId
      )(bust = true).map(_.get)
    } yield selected)(Future.successful)
  } yield s)()
}
