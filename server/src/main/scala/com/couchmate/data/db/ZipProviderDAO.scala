package com.couchmate.data.db

import com.couchmate.common.models.{Provider, ZipProvider}

class ZipProviderDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  def getZipProvidersForZip(zipCode: String) = ctx.run(quote {
    query[ZipProvider]
      .filter(_.zipCode == lift(zipCode))
  })

  def getProvidersForZip(zipCode: String) = ctx.run(quote {
    for {
      zp <- query[ZipProvider] if zp.zipCode == lift(zipCode)
      p <- query[Provider] if p.providerId.contains(zp.providerId)
    } yield p
  })

  def providerExistsForProviderAndZip(
    providerId: Long,
    zipCode: String,
  ) = ctx.run(quote {
    query[ZipProvider]
      .filter { zp =>
        zp.providerId == lift(providerId) &&
        zp.zipCode == lift(zipCode)
      }.nonEmpty
  })

  def insertZipProvider(zipProvider: ZipProvider) = ctx.run(quote {
    query[ZipProvider]
      .insert(lift(zipProvider))
      .returning(zp => zp)
  })

}
