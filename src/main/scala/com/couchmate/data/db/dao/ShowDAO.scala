package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.SportEventDAO.GetSportOrgFn
import com.couchmate.data.db.table.ShowTable
import com.couchmate.data.models.{Show, SportOrganization}

import scala.concurrent.{ExecutionContext, Future}

trait ShowDAO {

  def getShow(showId: Long)(
    implicit
    db: Database
  ): Future[Option[Show]] =
    db.run(ShowDAO.getShow(showId))

  def getShow$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Show], NotUsed] =
    Slick.flowWithPassThrough(ShowDAO.getShow)

  def getShowByExt(extId: Long)(
    implicit
    db: Database
  ): Future[Option[Show]] =
    db.run(ShowDAO.getShowByExt(extId))

  def getShowByExt$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Show], NotUsed] =
    Slick.flowWithPassThrough(ShowDAO.getShowByExt)

  def upsertShow(show: Show)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Show] =
    db.run(ShowDAO.upsertShow(show))

  def upsertShow$(show: Show)(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[Show, Show, NotUsed] =
    Slick.flowWithPassThrough(ShowDAO.upsertShow)

  def getOrAddShow(show: Show)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Show] =
    db.run(ShowDAO.getOrAddShow(show))

  def getOrAddShow$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[Show, Show, NotUsed] =
    Slick.flowWithPassThrough(ShowDAO.getOrAddShow)
}

object ShowDAO {
  private[this] lazy val getShowQuery = Compiled { (showId: Rep[Long]) =>
    ShowTable.table.filter(_.showId === showId)
  }

  private[dao] def getShow(showId: Long): DBIO[Option[Show]] =
    getShowQuery(showId).result.headOption

  private[this] lazy val getShowByExtQuery = Compiled { (extId: Rep[Long]) =>
    ShowTable.table.filter(_.extId === extId)
  }

  private[dao] def getShowByExt(extId: Long): DBIO[Option[Show]] =
    getShowByExtQuery(extId).result.headOption

  private[dao] def getShowByShow(show: Show): DBIO[Option[Show]] =
    show match {
      case Show(Some(showId), _, _, _, _, _, _, _) =>
        getShow(showId)
      case Show(None, extShowId, _, _, _, _, _, _) =>
        getShowByExt(extShowId)
      case _ => DBIO.successful(Option.empty)
    }

  private[dao] def upsertShow(show: Show)(
    implicit
    ec: ExecutionContext
  ): DBIO[Show] =
    show.showId.fold[DBIO[Show]](
      (ShowTable.table returning ShowTable.table) += show
    ) { (showId: Long) => for {
      _ <- ShowTable
        .table
        .filter(_.showId === showId)
        .update(show)
      updated <- ShowDAO.getShow(showId)
    } yield updated.get}

  private[dao] def getOrAddShow(show: Show)(
    implicit
    ec: ExecutionContext
  ): DBIO[Show] = (getShowByShow(show) flatMap {
    case Some(show) => DBIO.successful(show)
    case None => upsertShow(show)
  }).transactionally

}
