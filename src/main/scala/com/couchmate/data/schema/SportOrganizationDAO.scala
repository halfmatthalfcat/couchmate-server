package com.couchmate.data.schema

import PgProfile.api._
import com.couchmate.data.models.SportOrganization
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

class SportOrganizationDAO(tag: Tag) extends Table[SportOrganization](tag, "sport_organization") {
  def sportOrganizationId: Rep[Long] = column[Long]("sport_organization_id", O.PrimaryKey, O.AutoInc)
  def extSportId: Rep[Long] = column[Long]("ext_sport_id")
  def extOrgId: Rep[Option[Int]] = column[Option[Int]]("ext_org_id")
  def sportName: Rep[String] = column[String]("sport_name")
  def orgName: Rep[Option[String]] = column[Option[String]]("org_name")
  def * = (
    sportOrganizationId.?,
    extSportId,
    extOrgId,
    sportName,
    orgName,
  ) <> ((SportOrganization.apply _).tupled, SportOrganization.unapply)

  def sourceExtSportOrgIdx = index(
    "source_ext_sport_org_idx",
    (extSportId, extOrgId),
    unique = true
  )
}

object SportOrganizationDAO {
  val sportOrganizationTable = TableQuery[SportOrganizationDAO]

  val init = TableMigration(sportOrganizationTable)
    .create
    .addColumns(
      _.sportOrganizationId,
      _.extSportId,
      _.extOrgId,
      _.sportName,
      _.orgName,
    ).addIndexes(
      _.sourceExtSportOrgIdx,
    )

  def getSportOrganization(sportOrgnizationId: Long)(
    implicit
    db: Database,
  ): Future[Option[SportOrganization]] = {
    db.run(sportOrganizationTable.filter(_.sportOrganizationId === sportOrgnizationId).result.headOption)
  }

  def getSportOrganizationBySportAndOrg(
    extSportId: Long,
    extOrgId: Option[Int],
  )(
    implicit
    db: Database,
  ): Future[Option[SportOrganization]] = {
    db.run(sportOrganizationTable.filter { sportOrg =>
      sportOrg.extSportId === extSportId &&
      sportOrg.extOrgId === extOrgId
    }.result.headOption)
  }

  def upsertSportOrganization(sportOrganization: SportOrganization)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[SportOrganization] = {
    sportOrganization match {
      case SportOrganization(None, _, _, _, _) =>
        db.run((sportOrganizationTable returning sportOrganizationTable) += sportOrganization)
      case SportOrganization(Some(sportOrganizationId), _, _, _, _) => for {
        _ <- db.run(sportOrganizationTable.filter(_.sportOrganizationId === sportOrganizationId).update(sportOrganization))
        so <- db.run(sportOrganizationTable.filter(_.sportOrganizationId === sportOrganizationId).result.head)
      } yield so
    }
  }
}
