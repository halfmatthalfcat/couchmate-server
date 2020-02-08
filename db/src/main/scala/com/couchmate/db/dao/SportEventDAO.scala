package com.couchmate.db.dao

import com.couchmate.common.models.SportEvent
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.SportEventQueries
import com.couchmate.db.table.SportEventTable

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
