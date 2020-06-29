package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{Show, SportEvent, SportOrganization}
import com.couchmate.common.tables.SportEventTable

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

  def getOrAddSportEvent(
    show: Show,
    sportOrganization: SportOrganization,
    sportEvent: SportEvent
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Show] =
    db.run(SportEventDAO.getOrAddSportEvent(show, sportOrganization, sportEvent))

  def getOrAddSportEvent$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[(Show, SportOrganization, SportEvent), Show, NotUsed] =
    Slick.flowWithPassThrough(
      (SportEventDAO.getOrAddSportEvent _).tupled
    )
}

object SportEventDAO {
  type GetSportOrgFn = (Long, Option[Long]) => Future[SportOrganization]

  private[this] lazy val getSportEventQuery = Compiled { (sportEventId: Rep[Long]) =>
    SportEventTable.table.filter(_.sportEventId === sportEventId)
  }

  private[common] def getSportEvent(sportEventId: Long): DBIO[Option[SportEvent]] =
    getSportEventQuery(sportEventId).result.headOption

  private[this] lazy val getSportEventByNameAndOrgQuery = Compiled {
    (name: Rep[String], orgId: Rep[Long]) =>
      SportEventTable.table.filter { se =>
        se.sportEventTitle === name &&
        se.sportOrganizationId === orgId
      }
  }

  private[common] def getSportEventByNameAndOrg(
    name: String,
    orgId: Long
  ): DBIO[Option[SportEvent]] =
    getSportEventByNameAndOrgQuery(name, orgId).result.headOption

  private[common] def upsertSportEvent(sportEvent: SportEvent)(
    implicit
    ec: ExecutionContext
  ): DBIO[SportEvent] =
    sportEvent.sportEventId.fold[DBIO[SportEvent]](
      (SportEventTable.table returning SportEventTable.table) += sportEvent
    ) { (sportEventId: Long) => for {
      _ <- SportEventTable
        .table
        .filter(_.sportEventId === sportEventId)
        .update(sportEvent)
      updated <- SportEventDAO.getSportEvent(sportEventId)
    } yield updated.get}

  private[common] def getOrAddSportEvent(
    show: Show,
    sportOrganization: SportOrganization,
    sportEvent: SportEvent
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Show] = (ShowDAO.getShowByShow(show) flatMap {
    case Some(show) => DBIO.successful(show)
    case None => for {
      sportOrgExists <- SportOrganizationDAO.getSportOrganizationBySportAndOrg(
        sportOrganization.extSportId,
        sportOrganization.extOrgId
      )
      sportOrg <- sportOrgExists.fold(
        SportOrganizationDAO.upsertSportOrganization(sportOrganization)
      )(DBIO.successful)
      sportEventExists <- SportEventDAO.getSportEventByNameAndOrg(
        sportEvent.sportEventTitle,
        sportOrg.sportOrganizationId.get,
      )
      sportEvent <- sportEventExists.fold(
        upsertSportEvent(sportEvent.copy(
          sportOrganizationId = sportOrg.sportOrganizationId
        ))
      )(DBIO.successful)
      s <- ShowDAO.upsertShow(show.copy(
        sportEventId = sportEvent.sportEventId
      ))
    } yield s
  }).transactionally
}
