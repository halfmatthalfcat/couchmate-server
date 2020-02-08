package com.couchmate.db.dao

import com.couchmate.common.models.Show
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.ShowQueries
import com.couchmate.db.table.ShowTable

import scala.concurrent.{ExecutionContext, Future}

class ShowDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends ShowQueries {

  def getShow(showId: Long): Future[Option[Show]] = {
    db.run(super.getShow(showId).result.headOption)
  }

  def getShowByExt(extId: Long): Future[Option[Show]] = {
    db.run(super.getShowByExt(extId).result.headOption)
  }

  def upsertShow(show: Show): Future[Show] =
    show.showId.fold(
      db.run((ShowTable.table returning ShowTable.table) += show)
    ) { (showId: Long) => db.run(for {
      _ <- ShowTable.table.update(show)
      updated <- super.getShow(showId)
    } yield updated.result.head.transactionally)}
  
}
