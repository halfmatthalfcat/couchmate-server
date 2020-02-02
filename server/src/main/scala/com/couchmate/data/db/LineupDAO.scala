package com.couchmate.data.db

import com.couchmate.common.models.{Lineup, ProviderChannel}

class LineupDAO()(
  implicit
  val ctx: CMContext,
) {
  import ctx._

  private[this] implicit val lineupInsertMeta =
    insertMeta[Lineup](_.lineupId)

  def getLineup(lineupId: Long) = quote {
    query[Lineup]
      .filter(_.lineupId.orNull == lineupId)
  }

  def lineupsExistForProvider(providerId: Long) = quote {
    for {
      l <- query[Lineup]
      pc <- query[ProviderChannel] if (
        l.providerChannelId == pc.providerChannelId.orNull &&
        pc.providerId == providerId
      )
    } yield pc.providerId
  }

  def upsertLineup(lineup: Lineup) =  quote {
    query[Lineup]
      .insert(lift(lineup))
      .onConflictUpdate(_.lineupId)(
        (from, to) => from.replacedBy -> to.replacedBy
      ).returning(l => l)
  }

}
