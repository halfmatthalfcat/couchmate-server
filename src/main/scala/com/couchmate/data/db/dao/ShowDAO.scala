package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ShowTable
import com.couchmate.data.models.Show

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
}

object ShowDAO {
  private[dao] lazy val getShow = Compiled { (showId: Rep[Long]) =>
    ShowTable.table.filter(_.showId === showId)
  }

  private[dao] lazy val getShowByExt = Compiled { (extId: Rep[Long]) =>
    ShowTable.table.filter(_.extId === extId)
  }
}
