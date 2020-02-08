package com.couchmate.db.dao

import com.couchmate.common.models.SportOrganization
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.SportOrganizationQueries
import com.couchmate.db.table.SportOrganizationTable

import scala.concurrent.{ExecutionContext, Future}

class SportOrganizationDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends SportOrganizationQueries {

  def getSportOrganization(sportOrganizationId: Long): Future[Option[SportOrganization]] = {
    db.run(super.getSportOrganization(sportOrganizationId).result.headOption)
  }

  def getSportOrganizationBySportAndOrg(extSportId: Long, extOrgId: Option[Long]): Future[Option[SportOrganization]] = {
    db.run(super.getSportOrganizationBySportAndOrg(extSportId, extOrgId).result.headOption)
  }

  def upsertSportOrganization(sportOrganization: SportOrganization): Future[SportOrganization] =
    sportOrganization.sportOrganizationId.fold(
      db.run((SportOrganizationTable.table returning SportOrganizationTable.table) += sportOrganization)
    ) { (sportOrganizationId: Long) => db.run(for {
      _ <- SportOrganizationTable.table.update(sportOrganization)
      updated <- super.getSportOrganization(sportOrganizationId)
    } yield updated.result.head.transactionally)}

}
