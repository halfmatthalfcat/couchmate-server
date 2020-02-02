package com.couchmate.data.db

import com.couchmate.common.models.SportEvent

class SportEventDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  private[this] implicit val sportEventInsertMeta =
    insertMeta[SportEvent](_.sportEventId)

  def getSportEvent(sportEventId: Long) = quote {
    query[SportEvent]
      .filter(_.sportEventId.contains(sportEventId))
  }

  def upsertSportEvent(sportEvent: SportEvent) = quote {
    query[SportEvent]
      .insert(lift(sportEvent))
      .onConflictUpdate(_.sportEventId)(
        (from, to) => from.sportEventTitle -> to.sportEventTitle,
        (from, to) => from.sportOrganizationId -> to.sportOrganizationId,
      ).returning(se => se)
  }
}
