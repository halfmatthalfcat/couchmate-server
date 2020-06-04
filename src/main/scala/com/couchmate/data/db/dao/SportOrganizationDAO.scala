package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.SportOrganizationTable
import com.couchmate.data.models.SportOrganization

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
}

object SportOrganizationDAO {
  private[this] lazy val getSportOrganizationQuery = Compiled { (sportOrganizationId: Rep[Long]) =>
    SportOrganizationTable.table.filter(_.sportOrganizationId === sportOrganizationId)
  }

  private[dao] def getSportOrganization(sportOrganizationId: Long): DBIO[Option[SportOrganization]] =
    getSportOrganizationQuery(sportOrganizationId).result.headOption

  private[this] lazy val getSportOrganizationBySportAndOrgQuery = Compiled {
    (extSportId: Rep[Long], extOrgId: Rep[Option[Long]]) =>
      SportOrganizationTable.table.filter { so =>
        so.extSportId === extSportId &&
        so.extOrgId === extOrgId
      }
  }

  private[dao] def getSportOrganizationBySportAndOrg(
    extSportId: Long,
    extOrgId: Option[Long]
  ): DBIO[Option[SportOrganization]] =
    getSportOrganizationBySportAndOrgQuery(extSportId, extOrgId).result.headOption

  private[dao] def upsertSportOrganization(sportOrganization: SportOrganization)(
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
}
