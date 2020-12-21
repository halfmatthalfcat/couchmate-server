package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{Show, SportEvent, SportEventTeam, SportOrganization, SportTeam}
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

  def addOrGetSportEvent(sportEvent: SportEvent)(
    implicit
    db: Database
  ): Future[SportEvent] =
    db.run(SportEventDAO.addOrGetSportEvent(sportEvent).head)

  def getOrAddSportEvent(
    show: Show,
    sportOrganization: SportOrganization,
    sportEvent: SportEvent,
    teams: Seq[SportTeam],
    homeId: Option[Long]
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Show] = for {
    so <- db.run(SportOrganizationDAO.addOrGetSportOrganization(sportOrganization).head)
    se <- db.run(SportEventDAO.addOrGetSportEvent(sportEvent.copy(
      sportOrganizationId = so.sportOrganizationId
    )).head)
    ts <- Future.sequence(teams.map(team => db.run(SportTeamDAO.addOrGetSportTeam(team)))).map(_.flatten)
    _ <- Future.sequence(ts.map(team => db.run(SportEventTeamDAO.getOrAddSportEventTeam(SportEventTeam(
      sportEventId = se.sportEventId.get,
      sportTeamId = team.sportTeamId.get,
      isHome = homeId.contains(team.extSportTeamId)
    ))))).map(_.flatten)
    s <- db.run(ShowDAO.addOrGetShow(show.copy(
      sportEventId = se.sportEventId
    )).head)
  } yield s
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

  private[common] def addOrGetSportEvent(se: SportEvent) =
    sql"""
         WITH input_rows(sport_organization_id, sport_event_title) AS (
          VALUES (${se.sportOrganizationId}, ${se.sportEventTitle})
         ), ins AS (
          INSERT INTO sport_event (sport_organization_id, sport_event_title)
          SELECT * FROM input_rows
          ON CONFLICT (sport_organization_id, sport_event_title) DO NOTHING
          RETURNING sport_event_id, sport_organization_id, sport_event_title
         ), sel AS (
          SELECT sport_event_id, sport_organization_id, sport_event_title
          FROM ins
          UNION ALL
          SELECT se.sport_event_id, sport_organization_id, sport_event_title
          FROM input_rows
          JOIN sport_event AS se USING (sport_organization_id, sport_event_title)
         ), ups AS (
           INSERT INTO sport_event AS se (sport_organization_id, sport_event_title)
           SELECT i.*
           FROM   input_rows i
           LEFT   JOIN sel   s USING (sport_organization_id, sport_event_title)
           WHERE  s.sport_organization_id IS NULL
           ON     CONFLICT (sport_organization_id, sport_event_title) DO UPDATE
           SET    sport_organization_id = excluded.sport_organization_id,
                  sport_event_title = excluded.sport_event_title
           RETURNING sport_event_id, sport_organization_id, sport_event_title
         )  SELECT sport_event_id, sport_organization_id, sport_event_title FROM sel
            UNION  ALL
            TABLE  ups;
         """.as[SportEvent]
}
