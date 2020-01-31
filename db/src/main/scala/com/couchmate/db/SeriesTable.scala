package com.couchmate.db

import com.couchmate.common.models.Series
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

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
  val table = TableQuery[SeriesTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.seriesId,
      _.extId,
      _.seriesName,
      _.totalSeasons,
      _.totalEpisodes,
    )

//  private[this] lazy val getSeriesComplied = Compiled { (seriesId: Rep[Long]) =>
//    seriesTable.filter(_.seriesId === seriesId)
//  }
//
//  def getSeries(seriesId: Long): AppliedCompiledFunction[Long, Query[SeriesTable, SeriesTable, Seq], Seq[SeriesTable]] = {
//    getSeriesComplied(seriesId)
//  }
//
//  private[this] lazy val getSeriesByExtCompiled = Compiled { (extId: Rep[Long]) =>
//    seriesTable.filter(_.extId === extId)
//  }
//
//  def getSeriesByExt(extId: Long): AppliedCompiledFunction[Long, Query[SeriesTable, SeriesTable, Seq], Seq[SeriesTable]] = {
//    getSeriesByExtCompiled(extId)
//  }
//
//  def upsertSeries(s: SeriesTable): SqlStreamingAction[Vector[SeriesTable], SeriesTable, Effect] = {
//    sql"""
//         INSERT INTO series
//         (series_id, ext_id, series_name, total_seasons, total_episodes)
//         VALUES
//         (${s.seriesId}, ${s.extId}, ${s.seriesName}, ${s.totalSeasons}, ${s.totalEpisodes})
//         ON CONFLICT (series_id)
//         DO UPDATE SET
//            ext_id = ${s.extId},
//            series_name = ${s.seriesName},
//            total_seasons = ${s.totalSeasons},
//            total_episodes = ${s.totalEpisodes}
//         RETURNING *
//       """.as[SeriesTable]
//  }
}
