package com.couchmate.data.db.query

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ProviderTable

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
