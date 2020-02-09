package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.SportOrganizationTable
import com.couchmate.data.models.SportOrganization

import scala.concurrent.{ExecutionContext, Future}

class SportOrganizationDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getSportOrganization(sportOrganizationId: Long): Future[Option[SportOrganization]] = {
    db.run(SportOrganizationDAO.getSportOrganization(sportOrganizationId).result.headOption)
  }

  def getSportOrganizationBySportAndOrg(extSportId: Long, extOrgId: Option[Long]): Future[Option[SportOrganization]] = {
    db.run(SportOrganizationDAO.getSportOrganizationBySportAndOrg(extSportId, extOrgId).result.headOption)
  }

  def upsertSportOrganization(sportOrganization: SportOrganization): Future[SportOrganization] = db.run(
    sportOrganization.sportOrganizationId.fold[DBIO[SportOrganization]](
      (SportOrganizationTable.table returning SportOrganizationTable.table) += sportOrganization
    ) { (sportOrganizationId: Long) => for {
      _ <- SportOrganizationTable.table.update(sportOrganization)
      updated <- SportOrganizationDAO.getSportOrganization(sportOrganizationId).result.head
    } yield updated}.transactionally
  )
}

object SportOrganizationDAO {
  private[dao] lazy val getSportOrganization = Compiled { (sportOrganizationId: Rep[Long]) =>
    SportOrganizationTable.table.filter(_.sportOrganizationId === sportOrganizationId)
  }

  private[dao] lazy val getSportOrganizationBySportAndOrg = Compiled {
    (extSportId: Rep[Long], extOrgId: Rep[Option[Long]]) =>
      SportOrganizationTable.table.filter { so =>
        so.extSportId === extSportId &&
        so.extOrgId === extOrgId
      }
  }
}
