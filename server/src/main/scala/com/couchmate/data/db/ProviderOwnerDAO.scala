package com.couchmate.data.db

import com.couchmate.common.models.ProviderOwner

class ProviderOwnerDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  private[this] implicit val poInsertMeta =
    insertMeta[ProviderOwner](_.providerOwnerId)

  def getProviderOwner(providerOwnerId: Long) = ctx.run(quote {
    query[ProviderOwner]
      .filter(_.providerOwnerId.contains(lift(providerOwnerId)))
  })

  def getProviderOwnerForName(name: String) = ctx.run(quote {
    query[ProviderOwner]
      .filter(_.name == lift(name))
  })

  def getProviderOwnerForExt(extProviderOwnerId: String) = ctx.run(quote {
    query[ProviderOwner]
      .filter(_.extProviderOwnerId.contains(lift(extProviderOwnerId)))
  })

  def upsertProviderOwner(providerOwner: ProviderOwner) = ctx.run(quote {
    query[ProviderOwner]
      .insert(lift(providerOwner))
      .onConflictUpdate(_.providerOwnerId)(
        (from, to) => from.extProviderOwnerId -> to.extProviderOwnerId,
        (from, to) => from.name -> to.name,
      ).returning(po => po)
  })

}
