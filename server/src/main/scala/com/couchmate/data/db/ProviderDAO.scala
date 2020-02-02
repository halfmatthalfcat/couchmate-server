package com.couchmate.data.db

import com.couchmate.common.models.Provider

class ProviderDAO()(
  implicit
  val ctx: CMContext,
) {
  import ctx._

  private[this] implicit val pInsertMeta =
    insertMeta[Provider](_.providerId)

  def upsertProvider(provider: Provider) = quote {
    query[Provider]
      .insert(lift(provider))
      .onConflictUpdate(_.providerId)(
        (from, to) => from.providerOwnerId -> to.providerOwnerId,
        (from, to) => from.extId -> to.extId,
        (from, to) => from.`type` -> to.`type`,
        (from, to) => from.location -> to.location,
        (from, to) => from.name -> to.name,
      ).returning(p => p)
    }

}
