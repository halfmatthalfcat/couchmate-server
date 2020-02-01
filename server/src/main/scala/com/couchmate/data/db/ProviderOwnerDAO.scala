package com.couchmate.data.db

import com.couchmate.common.models.ProviderOwner
import io.getquill.{PostgresJdbcContext, SnakeCase}

class ProviderOwnerDAO()(
  implicit
  ctx: PostgresJdbcContext[SnakeCase.type],
) {
  import ctx._

  private[this] implicit val poInsertMeta =
    insertMeta[ProviderOwner](_.providerOwnerId)

  def upsertProviderOwner(providerOwner: ProviderOwner) = ctx.run(quote {
    query[ProviderOwner]
      .insert(lift(providerOwner))
      .onConflictUpdate(_.providerOwnerId)(
        (from, to) => from.extProviderOwnerId -> to.extProviderOwnerId,
        (from, to) => from.name -> to.name,
      ).returning(po => po)
  })

}
