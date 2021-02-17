package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.SportOrganization
import com.couchmate.common.tables.SportOrganizationTable

import scala.concurrent.{ExecutionContext, Future}

trait SportOrganizationDAO {

  def getSportOrganization(sportOrganizationId: Long)(
    implicit
    db: Database
  ): Future[Option[SportOrganization]] =
    db.run(SportOrganizationDAO.getSportOrganization(sportOrganizationId))

  def getSportOrganization$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[SportOrganization], NotUsed] =
    Slick.flowWithPassThrough(SportOrganizationDAO.getSportOrganization)

  def getSportOrganizationBySportAndOrg(extSportId: Long, extOrgId: Option[Long])(
    implicit
    db: Database
  ): Future[Option[SportOrganization]] =
    db.run(SportOrganizationDAO.getSportOrganizationBySportAndOrg(extSportId, extOrgId))

  def getSportOrganizationBySportAndOrg$()(
    implicit
    session: SlickSession
  ): Flow[(Long, Option[Long]), Option[SportOrganization], NotUsed] =
    Slick.flowWithPassThrough(
      (SportOrganizationDAO.getSportOrganizationBySportAndOrg _).tupled
    )

  def upsertSportOrganization(sportOrganization: SportOrganization)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[SportOrganization] =
    db.run(SportOrganizationDAO.upsertSportOrganization(sportOrganization))

  def upsertSportOrganization$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[SportOrganization, SportOrganization, NotUsed] =
    Slick.flowWithPassThrough(SportOrganizationDAO.upsertSportOrganization)

  def addOrGetSportOrganization(sportOrganization: SportOrganization)(
    implicit
    db: Database
  ): Future[SportOrganization] =
    db.run(SportOrganizationDAO.addOrGetSportOrganization(sportOrganization).head)
}

object SportOrganizationDAO {
  private[this] lazy val getSportOrganizationQuery = Compiled { (sportOrganizationId: Rep[Long]) =>
    SportOrganizationTable.table.filter(_.sportOrganizationId === sportOrganizationId)
  }

  private[common] def getSportOrganization(sportOrganizationId: Long): DBIO[Option[SportOrganization]] =
    getSportOrganizationQuery(sportOrganizationId).result.headOption

  private[this] lazy val getSportOrganizationBySportAndOrgQuery = Compiled {
    (extSportId: Rep[Long], extOrgId: Rep[Option[Long]]) =>
      SportOrganizationTable.table.filter { so =>
        so.extSportId === extSportId &&
        so.extOrgId === extOrgId
      }
  }

  private[common] def getSportOrganizationBySportAndOrg(
    extSportId: Long,
    extOrgId: Option[Long]
  ): DBIO[Option[SportOrganization]] =
    getSportOrganizationBySportAndOrgQuery(extSportId, extOrgId).result.headOption

  private[common] def upsertSportOrganization(sportOrganization: SportOrganization)(
    implicit
    ec: ExecutionContext
  ): DBIO[SportOrganization] =
    sportOrganization.sportOrganizationId.fold[DBIO[SportOrganization]](
      (SportOrganizationTable.table returning SportOrganizationTable.table) += sportOrganization
    ) { (sportOrganizationId: Long) => for {
      _ <- SportOrganizationTable
        .table
        .filter(_.sportOrganizationId === sportOrganizationId)
        .update(sportOrganization)
      updated <- SportOrganizationDAO.getSportOrganization(sportOrganizationId)
    } yield updated.get}

  private[this] def addSportOrganizationForId(so: SportOrganization) =
    sql"""
          WITH row AS (
            INSERT INTO sport_organization
            (ext_sport_id, ext_org_id, sport_name, org_name)
            VALUES
            (${so.extSportId}, ${so.extOrgId}, ${so.sportName}, ${so.orgName})
            ON CONFLICT (ext_sport_id, ext_org_id)
            DO NOTHING
            RETURNING sport_organization_id
          ) SELECT sport_organization_id FROM row
            UNION SELECT sport_organization_id FROM sport_organization
            WHERE ext_sport_id = ${so.extSportId} AND
                  ext_org_id = ${so.extOrgId}
         """.as[Long]

  private[common] def addAndGetSportOrganization(so: SportOrganization)(
    implicit
    ec: ExecutionContext
  ): DBIO[SportOrganization] = (for {
    sportOrganizationId <- addSportOrganizationForId(so).head
    sportOrganization <- getSportOrganizationQuery(sportOrganizationId).result.head
  } yield sportOrganization).transactionally

  private[common] def addOrGetSportOrganization(so: SportOrganization) =
    sql"""
         WITH input_rows(ext_sport_id, ext_org_id, sport_name, org_name) AS (
          VALUES (${so.extSportId}, ${so.extOrgId}, ${so.sportName}, ${so.orgName})
         ), ins AS (
          INSERT INTO sport_organization (ext_sport_id, ext_org_id, sport_name, org_name)
          SELECT * FROM input_rows
          ON CONFLICT (ext_sport_id, ext_org_id) DO NOTHING
          RETURNING sport_organization_id, ext_sport_id, ext_org_id, sport_name, org_name
         ), sel AS (
          SELECT sport_organization_id, ext_sport_id, ext_org_id, sport_name, org_name
          FROM ins
          UNION ALL
          SELECT so.sport_organization_id, ext_sport_id, ext_org_id, so.sport_name, so.org_name
          FROM input_rows
          JOIN sport_organization AS so USING (ext_sport_id, ext_org_id)
         ), ups AS (
           INSERT INTO sport_organization AS so (ext_sport_id, ext_org_id, sport_name, org_name)
           SELECT i.*
           FROM   input_rows i
           LEFT   JOIN sel   s USING (ext_sport_id, ext_org_id)
           WHERE  s.ext_sport_id IS NULL
           ON     CONFLICT (ext_sport_id, ext_org_id) DO UPDATE
           SET    ext_sport_id = excluded.ext_sport_id,
                  ext_org_id = excluded.ext_org_id,
                  sport_name = excluded.sport_name,
                  org_name = excluded.org_name
           RETURNING sport_organization_id, ext_sport_id, ext_org_id, sport_name, org_name
         )  SELECT sport_organization_id, ext_sport_id, ext_org_id, sport_name, org_name FROM sel
            UNION  ALL
            TABLE  ups;
         """.as[SportOrganization]
}
