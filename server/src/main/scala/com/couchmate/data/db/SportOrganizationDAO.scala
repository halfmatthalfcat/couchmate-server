package com.couchmate.data.db

import com.couchmate.common.models.SportOrganization

class SportOrganizationDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  private[this] implicit val sportOrganizationInsertMeta =
    insertMeta[SportOrganization](_.sportOrganizationId)

  def getSportOrganization(sportOrganizationId: Long) = quote {
    query[SportOrganization]
      .filter(_.sportOrganizationId.contains(sportOrganizationId))
  }

  def getSportOrganizationBySportAndOrg(
    extSportId: Long,
    extOrgId: Option[Int],
  ) = quote {
    query[SportOrganization]
      .filter { so =>
        so.extSportId == extSportId &&
        so.extOrgId == extOrgId
      }
  }

  def upsertSportOrganization(sportOrganization: SportOrganization) = quote {
    query[SportOrganization]
      .insert(lift(sportOrganization))
      .onConflictUpdate(_.sportOrganizationId)(
        (from, to) => from.extSportId -> to.extSportId,
        (from, to) => from.extOrgId -> to.extOrgId,
        (from, to) => from.orgName -> to.orgName,
        (from, to) => from.sportName -> to.sportName,
      ).returning(so => so)
  }

}
