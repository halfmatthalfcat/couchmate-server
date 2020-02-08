package com.couchmate.data.db.query

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{ProviderTable, ZipProviderTable}

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
