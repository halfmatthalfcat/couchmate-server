package com.couchmate.db.query

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.ProviderTable

trait ProviderQueries {

  private[db] lazy val getProvider = Compiled { (providerId: Rep[Long]) =>
    ProviderTable.table.filter(_.providerId === providerId)
  }

  private[db] lazy val getProviderForExtAndOwner = Compiled {
    (extId: Rep[String], providerOwnerId: Rep[Option[Long]]) =>
      ProviderTable.table.filter { p =>
        p.extId === extId &&
        p.providerOwnerId === providerOwnerId
      }
  }

}
