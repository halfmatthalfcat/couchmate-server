package com.couchmate.data.db

import com.couchmate.common.models.SportEvent

class SportEventDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  private[this] implicit val sportEventInsertMeta =
    insertMeta[SportEvent](_.sportEventId)

  def getSportEvent(sportEventId: Long) = ctx.run(quote {
    query[SportEvent]
      .filter(_.sportEventId.contains(sportEventId))
  }).headOption

  def getSportEventFromNameAndOrg(name: String, orgId: Long) = ctx.run(quote {
    query[SportEvent]
      .filter { se =>
        se.sportEventTitle == name &&
        se.sportOrganizationId == orgId
      }
  }).headOption

  def upsertSportEvent(sportEvent: SportEvent) = ctx.run(quote {
    query[SportEvent]
      .insert(lift(sportEvent))
      .onConflictUpdate(_.sportEventId)(
        (from, to) => from.sportEventTitle -> to.sportEventTitle,
        (from, to) => from.sportOrganizationId -> to.sportOrganizationId,
      ).returning(se => se)
  })
}
