package com.couchmate.data.db.dao

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.SeriesTable
import com.couchmate.data.models.Series
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

class SeriesDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getSeries(seriesId: Long): Future[Option[Series]] = {
    db.run(SeriesDAO.getSeries(seriesId).result.headOption)
  }

  def getSeriesByExt(extId: Long): Future[Option[Series]] = {
    db.run(SeriesDAO.getSeriesByExt(extId).result.headOption)
  }

  def upsertSeries(series: Series): Future[Series] = db.run(
    series.seriesId.fold[DBIO[Series]](
      (SeriesTable.table returning SeriesTable.table) += series
    ) { (seriesId: Long) => for {
      _ <- SeriesTable.table.update(series)
      updated <- SeriesDAO.getSeries(seriesId).result.head
    } yield updated}.transactionally
  )
}

object SeriesDAO {
  private[dao] lazy val getSeries = Compiled { (seriesId: Rep[Long]) =>
    SeriesTable.table.filter(_.seriesId === seriesId)
  }

  private[dao] lazy val getSeriesByExt = Compiled { (extId: Rep[Long]) =>
    SeriesTable.table.filter(_.extId === extId)
  }
}
