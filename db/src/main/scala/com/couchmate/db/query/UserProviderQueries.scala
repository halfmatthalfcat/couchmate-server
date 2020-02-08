package com.couchmate.db.query

import java.util.UUID

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.{ProviderTable, UserProviderTable}

trait UserProviderQueries {

  private[db] lazy val getUserProvider = Compiled { (userId: Rep[UUID]) =>
    UserProviderTable.table.filter(_.userId === userId)
  }

  private[db] lazy val getProviders = Compiled { (userId: Rep[UUID]) =>
    for {
      p <- ProviderTable.table
      up <- UserProviderTable.table if ( up.userId === userId )
    } yield p
  }

  private[db] lazy val userProviderExists = Compiled {
    (providerId: Rep[Long], zipCode: Rep[String]) =>
      UserProviderTable.table.filter { up =>
        up.providerId === providerId &&
        up.zipCode === zipCode
      }.exists
  }

  private[db] lazy val getUniqueInternalProviders = Compiled {
    UserProviderTable.table.distinct
  }

  private[db] lazy val getUniqueProviders = Compiled {
    (for {
      p <- ProviderTable.table
      up <- UserProviderTable.table if ( p.providerId === up.providerId )
    } yield p.extId).distinct
  }

}
