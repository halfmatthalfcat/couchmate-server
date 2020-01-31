package com.couchmate.db

import com.couchmate.common.models.SportOrganization
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class SportOrganizationTable(tag: Tag) extends Table[SportOrganization](tag, "sport_organization") {
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

object SportOrganizationTable extends Slickable[SportOrganizationTable] {
  val table = TableQuery[SportOrganizationTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
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

//  private[this] lazy val getSportOrganizationCompiled = Compiled { (sportOrganizationId: Rep[Long]) =>
//    sportOrganizationTable.filter(_.sportOrganizationId === sportOrganizationId)
//  }
//
//  def getSportOrganization(sportOrganizationId: Long): AppliedCompiledFunction[Long, Query[SportOrganizationTable, SportOrganization, Seq], Seq[SportOrganization]] = {
//    getSportOrganizationCompiled(sportOrganizationId)
//  }
//
//  private[this] lazy val getSportOrganizationBySportAndOrgCompiled = Compiled { (extSportId: Rep[Long], extOrgId: Rep[Option[Int]]) =>
//    sportOrganizationTable.filter { sportOrg =>
//      sportOrg.extSportId === extSportId &&
//      sportOrg.extOrgId === extOrgId
//    }
//  }
//
//  def getSportOrganizationBySportAndOrg(
//    extSportId: Long,
//    extOrgId: Option[Int],
//  ): AppliedCompiledFunction[(Long, Option[Int]), Query[SportOrganizationTable, SportOrganization, Seq], Seq[SportOrganization]] = {
//    getSportOrganizationBySportAndOrgCompiled(extSportId, extOrgId)
//  }
//
//  def upsertSportOrganization(so: SportOrganization): SqlStreamingAction[Vector[SportOrganization], SportOrganization, Effect] = {
//    sql"""
//         INSERT INTO sport_organization
//         (sport_organization_id, ext_sport_id, ext_org_id, sport_name, org_name)
//         VALUES
//         (${so.sportOrganizationId}, ${so.extSportId}, ${so.extOrgId}, ${so.sportName}, ${so.orgName})
//         ON CONFLICT (sport_organization_id)
//         DO UPDATE SET
//            ext_sport_id = ${so.extSportId},
//            ext_org_id = ${so.extOrgId},
//            sport_name = ${so.sportName},
//            org_name = ${so.orgName}
//         RETURNING *
//       """.as[SportOrganization]
//  }
}
