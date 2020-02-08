package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.SportEventQueries
import com.couchmate.data.db.table.SportEventTable
import com.couchmate.data.models.SportEvent

import scala.concurrent.{ExecutionContext, Future}

class SportEventDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends SportEventQueries {

  def getSportEvent(sportEventId: Long): Future[Option[SportEvent]] = {
    db.run(super.getSportEvent(sportEventId).result.headOption)
  }

  def getSportEventByNameAndOrg(name: String, orgId: Long): Future[Option[SportEvent]] = {
    db.run(super.getSportEventByNameAndOrg(name, orgId).result.headOption)
  }

  def upsertSportEvent(sportEvent: SportEvent): Future[SportEvent] =
    sportEvent.sportEventId.fold(
      db.run((SportEventTable.table returning SportEventTable.table) += sportEvent)
    ) { (sportEventId: Long) => db.run(for {
      _ <- SportEventTable.table.update(sportEvent)
      updated <- super.getSportEvent(sportEventId)
    } yield updated.result.head.transactionally)}

}
