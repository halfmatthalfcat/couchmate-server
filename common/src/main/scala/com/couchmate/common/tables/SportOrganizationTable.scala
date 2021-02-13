package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.SportOrganization
import com.couchmate.common.util.slick.WithTableQuery

class SportOrganizationTable(tag: Tag) extends Table[SportOrganization](tag, "sport_organization") {
  def sportOrganizationId: Rep[Long] = column[Long]("sport_organization_id", O.PrimaryKey, O.AutoInc)
  def extSportId: Rep[Long] = column[Long]("ext_sport_id")
  def extOrgId: Rep[Long] = column[Long]("ext_org_id")
  def sportName: Rep[String] = column[String]("sport_name")
  def orgName: Rep[Option[String]] = column[Option[String]]("org_name")
  def * = (
    sportOrganizationId.?,
    extSportId,
    extOrgId.?,
    sportName,
    orgName,
  ) <> ((SportOrganization.apply _).tupled, SportOrganization.unapply)

  def sourceExtSportOrgIdx = index(
    "source_ext_sport_org_idx",
    (extSportId, extOrgId),
    unique = true
  )
}

object SportOrganizationTable extends WithTableQuery[SportOrganizationTable] {
  private[couchmate] val table = TableQuery[SportOrganizationTable]
}
