package com.couchmate.data.db

import com.couchmate.common.models.Provider
import io.getquill.{PostgresJdbcContext, SnakeCase}

class ProviderDAO()(
  implicit
  val ctx: PostgresJdbcContext[SnakeCase.type],
) {
  import ctx._

  def upsertProvider(provider: Provider): Provider = ctx.run(quote {
    query[Provider]
      .insert(lift(provider))
      .onConflictUpdate(_.providerId)(
        (from, to) => from.providerOwnerId -> to.providerOwnerId,
        (from, to) => from.extId -> to.extId,
        (from, to) => from.`type` -> to.`type`,
        (from, to) => from.location -> to.location,
        (from, to) => from.name -> to.name,
      ).returning(p => p)
    })

}
