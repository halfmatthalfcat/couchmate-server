package com.couchmate.db.query

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.{ProviderTable, ZipProviderTable}

trait ZipProviderQueries {

  private[db] lazy val getZipProvidersForZip = Compiled { (zipCode: Rep[String]) =>
    ZipProviderTable.table.filter(_.zipCode === zipCode)
  }

  private[db] lazy val getProvidersForZip = Compiled { (zipCode: Rep[String]) =>
    for {
      zp <- ZipProviderTable.table if zp.zipCode === zipCode
      p <- ProviderTable.table if p.providerId === zp.providerId
    } yield p
  }

  private[db] lazy val providerExistsForProviderAndZip = Compiled {
    (providerId: Rep[Long], zipCode: Rep[String]) =>
      ZipProviderTable.table.filter { zp =>
        zp.providerId === providerId &&
        zp.zipCode === zipCode
      }.exists
  }

}
