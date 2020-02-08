package com.couchmate.db.query

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.SportEventTable

trait SportEventQueries {

  private[db] lazy val getSportEvent = Compiled { (sportEventId: Rep[Long]) =>
    SportEventTable.table.filter(_.sportEventId === sportEventId)
  }

  private[db] lazy val getSportEventByNameAndOrg = Compiled {
    (name: Rep[String], orgId: Rep[Long]) =>
      SportEventTable.table.filter { se =>
        se.sportEventTitle === name &&
        se.sportOrganizationId === orgId
      }
  }

}
