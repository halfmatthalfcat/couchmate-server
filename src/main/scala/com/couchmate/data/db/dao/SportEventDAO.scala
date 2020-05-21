package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{ShowTable, SportEventTable, SportOrganizationTable}
import com.couchmate.data.models.{Show, SportEvent, SportOrganization}
import com.couchmate.external.gracenote.models.GracenoteProgram

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

  private[dao] def getShowFromGracenoteSport(
    program: GracenoteProgram,
    orgFn: (Long, Option[Long]) => Future[SportOrganization],
  )(implicit ec: ExecutionContext): DBIO[Show] = for {
    orgExists <- SportOrganizationDAO.getSportOrganizationBySportAndOrg(
      program.sportsId.get,
      program.organizationId,
    ).result.headOption
    sportOrg <- orgExists.fold[DBIO[SportOrganization]](for {
      gnSO <- DBIO.from(orgFn(
        program.sportsId.get,
        program.organizationId,
      ))
      so <- (SportOrganizationTable.table returning SportOrganizationTable.table) += gnSO
    } yield so)(DBIO.successful)
    sportEventExists <- SportEventDAO.getSportEventByNameAndOrg(
      program.eventTitle.getOrElse(program.title),
      sportOrg.sportOrganizationId.get,
    ).result.headOption
    sportEvent <- sportEventExists.fold[DBIO[SportEvent]](
      (SportEventTable.table returning SportEventTable.table) += SportEvent(
        sportEventId = None,
        sportEventTitle = program.eventTitle.getOrElse(program.title),
        sportOrganizationId = sportOrg.sportOrganizationId.get,
      )
    )(DBIO.successful)
    show <- (ShowTable.table returning ShowTable.table) += Show(
      showId = None,
      extId = program.rootId,
      `type` = "sport",
      episodeId = None,
      sportEventId = sportEvent.sportEventId,
      title = program.title,
      description = program
        .shortDescription
        .orElse(program.longDescription)
        .getOrElse("N/A"),
      originalAirDate = program.origAirDate
    )
  } yield show
}
