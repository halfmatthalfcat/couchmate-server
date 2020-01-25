package com.couchmate.data.schema

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.Series
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class SeriesDAO(tag: Tag) extends Table[Series](tag, "series") {
  def seriesId: Rep[Long] = column[Long]("series_id", O.PrimaryKey, O.AutoInc)
  def sourceId: Rep[Long] = column[Long]("source_id")
  def extId: Rep[Long] = column[Long]("ext_id")
  def seriesName: Rep[String] = column[String]("series_name")
  def totalSeasons: Rep[Long] = column[Long]("total_seasons")
  def totalEpisodes: Rep[Long] = column[Long]("total_episodes")
  def * = (
    seriesId.?,
    sourceId,
    extId,
    seriesName,
    totalSeasons.?,
    totalEpisodes.?,
  ) <> ((Series.apply _).tupled, Series.unapply)

  def sourceFk = foreignKey(
    "series_source_fk",
    sourceId,
    SourceDAO.sourceTable,
  )(
    _.sourceId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def sourceExtIdx = index(
    "series_source_ext_idx",
    (sourceId, extId),
    unique = true
  )
}

object SeriesDAO {
  val seriesTable = TableQuery[SeriesDAO]

  val init = TableMigration(seriesTable)
    .create
    .addColumns(
      _.seriesId,
      _.sourceId,
      _.extId,
      _.seriesName,
      _.totalSeasons,
      _.totalEpisodes,
    ).addForeignKeys(
      _.sourceFk,
    ).addIndexes(
      _.sourceExtIdx,
    )

  def getSeries()(
    implicit
    session: SlickSession,
  ): Flow[Long, Option[Series], NotUsed] = Slick.flowWithPassThrough { seriesId =>
    seriesTable.filter(_.seriesId === seriesId).result.headOption
  }

  def getSeriesBySourceAndExt()(
    implicit
    session: SlickSession,
  ): Flow[(Long, Long), Option[Series], NotUsed] = Slick.flowWithPassThrough {
    case (sourceId, extId) => seriesTable.filter { series =>
      series.sourceId === sourceId &&
      series.extId === extId
    }.result.headOption
  }

  def upsertSeries(series: Series)(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[Series, Series, NotUsed] = Slick.flowWithPassThrough {
    case series @ Series(None, _, _, _, _, _) =>
      (seriesTable returning seriesTable) += series
    case series @ Series(Some(seriesId), _, _, _, _, _) => for {
      _ <- seriesTable.filter(_.seriesId === seriesId).update(series)
      s <- seriesTable.filter(_.seriesId === seriesId).result.head
    } yield s
  }
}
