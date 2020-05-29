package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{ShowTable, SportEventTable, SportOrganizationTable}
import com.couchmate.data.models.{Show, SportEvent, SportOrganization}

import scala.concurrent.{ExecutionContext, Future}

trait SportEventDAO {

  def getSportEvent(sportEventId: Long)(
    implicit
    db: Database
  ): Future[Option[SportEvent]] =
    db.run(SportEventDAO.getSportEvent(sportEventId))

  def getSportEvent$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[SportEvent], NotUsed] =
    Slick.flowWithPassThrough(SportEventDAO.getSportEvent)

  def getSportEventByNameAndOrg(name: String, orgId: Long)(
    implicit
    db: Database
  ): Future[Option[SportEvent]] =
    db.run(SportEventDAO.getSportEventByNameAndOrg(name, orgId))

  def getSportEventByNameAndOrg$()(
    implicit
    session: SlickSession
  ): Flow[(String, Long), Option[SportEvent], NotUsed] =
    Slick.flowWithPassThrough(
      (SportEventDAO.getSportEventByNameAndOrg _).tupled
    )

  def upsertSportEvent(sportEvent: SportEvent)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[SportEvent] =
    db.run(SportEventDAO.upsertSportEvent(sportEvent))

  def upsertSportEvent$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[SportEvent, SportEvent, NotUsed] =
    Slick.flowWithPassThrough(SportEventDAO.upsertSportEvent)
}

object SportEventDAO {
  type GetSportOrgFn = (Long, Option[Long]) => Future[SportOrganization]

  private[this] lazy val getSportEventQuery = Compiled { (sportEventId: Rep[Long]) =>
    SportEventTable.table.filter(_.sportEventId === sportEventId)
  }

  private[dao] def getSportEvent(sportEventId: Long): DBIO[Option[SportEvent]] =
    getSportEventQuery(sportEventId).result.headOption

  private[this] lazy val getSportEventByNameAndOrgQuery = Compiled {
    (name: Rep[String], orgId: Rep[Long]) =>
      SportEventTable.table.filter { se =>
        se.sportEventTitle === name &&
        se.sportOrganizationId === orgId
      }
  }

  private[dao] def getSportEventByNameAndOrg(
    name: String,
    orgId: Long
  ): DBIO[Option[SportEvent]] =
    getSportEventByNameAndOrgQuery(name, orgId).result.headOption

  private[dao] def upsertSportEvent(sportEvent: SportEvent)(
    implicit
    ec: ExecutionContext
  ): DBIO[SportEvent] =
    sportEvent.sportEventId.fold[DBIO[SportEvent]](
      (SportEventTable.table returning SportEventTable.table) += sportEvent
    ) { (sportEventId: Long) => for {
      _ <- SportEventTable.table.update(sportEvent)
      updated <- SportEventDAO.getSportEvent(sportEventId)
    } yield updated.get}
}
