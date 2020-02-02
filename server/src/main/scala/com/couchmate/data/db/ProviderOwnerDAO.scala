package com.couchmate.data.db

import com.couchmate.common.models.ProviderOwner

class ProviderOwnerDAO()(
  implicit
  val ctx: CMContext,
) {
  import ctx._

  private[this] implicit val poInsertMeta =
    insertMeta[ProviderOwner](_.providerOwnerId)

  def getProviderOwner(providerOwnerId: Long) = quote {
    query[ProviderOwner]
      .filter(_.providerOwnerId.contains(providerOwnerId))
  }

  def getProviderOwnerForName(name: String) = quote {
    query[ProviderOwner]
      .filter(_.name == name)
  }

  def getProviderOwnerForExt(extProviderOwnerId: String) = quote {
    query[ProviderOwner]
      .filter(_.extProviderOwnerId.orNull == extProviderOwnerId)
  }

  def upsertProviderOwner(providerOwner: ProviderOwner) = quote {
    query[ProviderOwner]
      .insert(lift(providerOwner))
      .onConflictUpdate(_.providerOwnerId)(
        (from, to) => from.extProviderOwnerId -> to.extProviderOwnerId,
        (from, to) => from.name -> to.name,
      ).returning(po => po)
  }

}
