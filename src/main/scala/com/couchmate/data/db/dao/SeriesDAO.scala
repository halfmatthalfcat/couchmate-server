package com.couchmate.data.db.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.SeriesTable
import com.couchmate.data.models.Series
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

trait SeriesDAO {

  def getSeries(seriesId: Long)(
    implicit
    db: Database
  ): Future[Option[Series]] = {
    db.run(SeriesDAO.getSeries(seriesId))
  }

  def getSeries$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Series], NotUsed] =
    Slick.flowWithPassThrough(SeriesDAO.getSeries)

  def getSeriesByExt(extId: Long)(
    implicit
    db: Database
  ): Future[Option[Series]] = {
    db.run(SeriesDAO.getSeriesByExt(extId))
  }

  def getSeriesByExt$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Series], NotUsed] =
    Slick.flowWithPassThrough(SeriesDAO.getSeriesByExt)

  def upsertSeries(series: Series)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Series] =
    db.run(SeriesDAO.upsertSeries(series))

  def upsertSeries$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[Series, Series, NotUsed] =
    Slick.flowWithPassThrough(SeriesDAO.upsertSeries)
}

object SeriesDAO {
  private[this] lazy val getSeriesQuery = Compiled { (seriesId: Rep[Long]) =>
    SeriesTable.table.filter(_.seriesId === seriesId)
  }

  private[dao] def getSeries(seriesId: Long): DBIO[Option[Series]] =
    getSeriesQuery(seriesId).result.headOption

  private[this] lazy val getSeriesByExtQuery = Compiled { (extId: Rep[Long]) =>
    SeriesTable.table.filter(_.extId === extId)
  }

  private[dao] def getSeriesByExt(extId: Long): DBIO[Option[Series]] =
    getSeriesByExtQuery(extId).result.headOption

  private[dao] def upsertSeries(series: Series)(
    implicit
    ec: ExecutionContext
  ): DBIO[Series] =
    series.seriesId.fold[DBIO[Series]](
      (SeriesTable.table returning SeriesTable.table) += series
    ) { (seriesId: Long) => for {
      _ <- SeriesTable
        .table
        .filter(_.seriesId === seriesId)
        .update(series)
      updated <- SeriesDAO.getSeries(seriesId)
    } yield updated.get}
}
