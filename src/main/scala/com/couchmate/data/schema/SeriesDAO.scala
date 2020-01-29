package com.couchmate.data.schema

import PgProfile.api._
import com.couchmate.data.models.Series
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

class SeriesDAO(tag: Tag) extends Table[Series](tag, "series") {
  def seriesId: Rep[Long] = column[Long]("series_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id")
  def seriesName: Rep[String] = column[String]("series_name")
  def totalSeasons: Rep[Option[Long]] = column[Option[Long]]("total_seasons")
  def totalEpisodes: Rep[Option[Long]] = column[Option[Long]]("total_episodes")
  def * = (
    seriesId.?,
    extId,
    seriesName,
    totalSeasons,
    totalEpisodes,
  ) <> ((Series.apply _).tupled, Series.unapply)
}

object SeriesDAO {
  val seriesTable = TableQuery[SeriesDAO]

  val init = TableMigration(seriesTable)
    .create
    .addColumns(
      _.seriesId,
      _.extId,
      _.seriesName,
      _.totalSeasons,
      _.totalEpisodes,
    )

  private[this] lazy val getSeriesComplied = Compiled { (seriesId: Rep[Long]) =>
    seriesTable.filter(_.seriesId === seriesId)
  }

  def getSeries(seriesId: Long) = {
    getSeriesComplied(seriesId)
  }

  def getSeriesByExt(extId: Long)(
    implicit
    db: Database,
  ): Future[Option[Series]] = {
    db.run(seriesTable.filter(_.extId === extId).result.headOption)
  }

  def upsertSeries(series: Series)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Series] = {
    series match {
      case Series(None, _, _, _, _) =>
        db.run((seriesTable returning seriesTable) += series)
      case Series(Some(seriesId), _, _, _, _) => for {
        _ <- db.run(seriesTable.filter(_.seriesId === seriesId).update(series))
        s <- db.run(seriesTable.filter(_.seriesId === seriesId).result.head)
      } yield s
    }
  }
}
