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
}
