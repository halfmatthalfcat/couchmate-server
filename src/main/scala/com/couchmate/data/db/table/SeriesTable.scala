package com.couchmate.data.db.table

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.Series
import slick.lifted.Tag
import slick.migration.api._

import scala.concurrent.ExecutionContext

class SeriesTable(tag: Tag) extends Table[Series](tag, "series") {
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

object SeriesTable extends Slickable[SeriesTable] {
  private[db] val table = TableQuery[SeriesTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.seriesId,
      _.extId,
      _.seriesName,
      _.totalSeasons,
      _.totalEpisodes,
    )

  private[db] def seed(implicit ec: ExecutionContext): Option[DBIO[_]] =
    Option.empty
}
