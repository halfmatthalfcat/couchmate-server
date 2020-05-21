package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ShowTable
import com.couchmate.data.models.{Show, SportOrganization}
import com.couchmate.external.gracenote.models.GracenoteProgram

import scala.concurrent.{ExecutionContext, Future}

class ShowDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getShow(showId: Long): Future[Option[Show]] = {
    db.run(ShowDAO.getShow(showId).result.headOption)
  }

  def getShowByExt(extId: Long): Future[Option[Show]] = {
    db.run(ShowDAO.getShowByExt(extId).result.headOption)
  }

  def upsertShow(show: Show): Future[Show] = db.run(
    show.showId.fold[DBIO[Show]](
      (ShowTable.table returning ShowTable.table) += show
    ) { (showId: Long) => for {
      _ <- ShowTable.table.update(show)
      updated <- ShowDAO.getShow(showId).result.head
    } yield updated}.transactionally
  )

  def getShowFromGracenoteProgram(
    program: GracenoteProgram,
    orgFn: (Long, Option[Long]) => Future[SportOrganization],
  ): Future[Show] = db.run(
    ShowDAO.getShowFromGracenoteProgram(program, orgFn).transactionally
  )
}

object ShowDAO {
  private[dao] lazy val getShow = Compiled { (showId: Rep[Long]) =>
    ShowTable.table.filter(_.showId === showId)
  }

  private[dao] lazy val getShowByExt = Compiled { (extId: Rep[Long]) =>
    ShowTable.table.filter(_.extId === extId)
  }

  private[dao] def getShowFromGracenoteProgram(
    program: GracenoteProgram,
    orgFn: (Long, Option[Long]) => Future[SportOrganization],
  )(
    implicit
    ec: ExecutionContext,
  ): DBIO[Show] = (for {
    exists <- getShowByExt(program.rootId).result.headOption
    show <- exists.fold(
      if (program.isSport) {
        SportEventDAO.getShowFromGracenoteSport(
          program,
          orgFn,
        )
      } else if (program.isSeries) {
        EpisodeDAO.getShowFromGracenoteEpisode(program)
      } else {
        (ShowTable.table returning ShowTable.table) += Show(
          showId = None,
          extId = program.rootId,
          `type` = "show",
          episodeId = None,
          sportEventId = None,
          title = program.title,
          description = program
            .shortDescription
            .orElse(program.longDescription)
            .getOrElse("N/A"),
          originalAirDate = program.origAirDate
        )
      }
    )(DBIO.successful)
  } yield show)
}
