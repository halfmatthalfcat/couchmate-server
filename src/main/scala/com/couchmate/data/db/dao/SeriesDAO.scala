package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.SeriesQueries
import com.couchmate.data.db.table.SeriesTable
import com.couchmate.data.models.Series

import scala.concurrent.{ExecutionContext, Future}

class SeriesDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends SeriesQueries {

  def getSeries(seriesId: Long): Future[Option[Series]] = {
    db.run(super.getSeries(seriesId).result.headOption)
  }

  def getSeriesByExt(extId: Long): Future[Option[Series]] = {
    db.run(super.getSeriesByExt(extId).result.headOption)
  }

  def upsertSeries(series: Series): Future[Series] =
    series.seriesId.fold(
      db.run((SeriesTable.table returning SeriesTable.table) += series)
    ) { (seriesId: Long) => db.run(for {
      _ <- SeriesTable.table.update(series)
      updated <- super.getSeries(seriesId)
    } yield updated.result.head.transactionally)}

}
