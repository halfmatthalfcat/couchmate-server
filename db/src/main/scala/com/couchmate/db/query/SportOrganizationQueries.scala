package com.couchmate.db.query

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.SportOrganizationTable

trait SportOrganizationQueries {

  private[db] lazy val getSportOrganization = Compiled { (sportOrganizationId: Rep[Long]) =>
    SportOrganizationTable.table.filter(_.sportOrganizationId === sportOrganizationId)
  }

  private[db] lazy val getSportOrganizationBySportAndOrg = Compiled {
    (extSportId: Rep[Long], extOrgId: Rep[Option[Long]]) =>
      SportOrganizationTable.table.filter { so =>
        so.extSportId === extSportId &&
        so.extOrgId === extOrgId
      }
  }

}
