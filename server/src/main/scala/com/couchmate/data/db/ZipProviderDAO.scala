package com.couchmate.data.db

import com.couchmate.common.models.{Provider, ZipProvider}

class ZipProviderDAO()(
  implicit
  val ctx: CMContext,
) {
  import ctx._

  def getZipProvidersForZip(zipCode: String) = quote {
    query[ZipProvider]
      .filter(_.zipCode == zipCode)
  }

  def getProvidersForZip(zipCode: String) = quote {
    for {
      zp <- query[ZipProvider]
      p <- query[Provider] if p.providerId.contains(zp.providerId)
    } yield p
  }

  def providerExistsForProviderAndZip(
    providerId: Long,
    zipCode: String,
  ) = quote {
    query[ZipProvider]
      .filter { zp =>
        zp.providerId == providerId &&
        zp.zipCode == zipCode
      }
  }

  def insertZipProvider(zipProvider: ZipProvider) = quote {
    query[ZipProvider]
      .insert(lift(zipProvider))
      .returning(zp => zp)
  }

}
