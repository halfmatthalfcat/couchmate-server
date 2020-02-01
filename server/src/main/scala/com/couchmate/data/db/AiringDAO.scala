package com.couchmate.data.db

import java.util.UUID

import com.couchmate.common.models.Airing
import io.getquill.{PostgresJdbcContext, SnakeCase}

class AiringDAO()(
  implicit
  val ctx: PostgresJdbcContext[SnakeCase.type],
) {
  import ctx._

  def upsertAiring(airing: Airing) = ctx.run(quote {
    query[Airing]
      .insert(lift(airing))
      .onConflictUpdate(_.airingId)(
        (from, to) => from.showId -> to.showId,
        (from, to) => from.startTime -> to.startTime,
        (from, to) => from.endTime -> to.endTime,
        (from, to) => from.duration -> to.duration,
      ).returningGenerated(a => a)
  })

}
