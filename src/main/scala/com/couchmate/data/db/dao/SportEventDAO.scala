package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.SportEventTable
import com.couchmate.data.models.SportEvent

import scala.concurrent.{ExecutionContext, Future}

class SportEventDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getSportEvent(sportEventId: Long): Future[Option[SportEvent]] = {
    db.run(SportEventDAO.getSportEvent(sportEventId).result.headOption)
  }

  def getSportEventByNameAndOrg(name: String, orgId: Long): Future[Option[SportEvent]] = {
    db.run(SportEventDAO.getSportEventByNameAndOrg(name, orgId).result.headOption)
  }

  def upsertSportEvent(sportEvent: SportEvent): Future[SportEvent] = db.run(
    sportEvent.sportEventId.fold[DBIO[SportEvent]](
      (SportEventTable.table returning SportEventTable.table) += sportEvent
    ) { (sportEventId: Long) => for {
      _ <- SportEventTable.table.update(sportEvent)
      updated <- SportEventDAO.getSportEvent(sportEventId).result.head
    } yield updated}.transactionally
  )
}

object SportEventDAO {
  private[dao] lazy val getSportEvent = Compiled { (sportEventId: Rep[Long]) =>
    SportEventTable.table.filter(_.sportEventId === sportEventId)
  }

  private[dao] lazy val getSportEventByNameAndOrg = Compiled {
    (name: Rep[String], orgId: Rep[Long]) =>
      SportEventTable.table.filter { se =>
        se.sportEventTitle === name &&
        se.sportOrganizationId === orgId
      }
  }
}
