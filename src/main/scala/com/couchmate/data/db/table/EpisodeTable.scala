package com.couchmate.data.db.table

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.Episode
import slick.lifted.Tag
import slick.migration.api._

import scala.concurrent.ExecutionContext

class EpisodeTable(tag: Tag) extends Table[Episode](tag, "episode") {
  def episodeId: Rep[Long] = column[Long]("episode_id", O.PrimaryKey, O.AutoInc)
  def seriesId: Rep[Option[Long]] = column[Option[Long]]("series_id")
  def season: Rep[Option[Long]] = column[Option[Long]]("season")
  def episode: Rep[Option[Long]] = column[Option[Long]]("episode")
  def * = (
    episodeId.?,
    seriesId,
    season,
    episode,
  ) <> ((Episode.apply _).tupled, Episode.unapply)

  def seriesFk = foreignKey(
    "series_episode_fk",
    seriesId,
    SeriesTable.table,
    )(
    _.seriesId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object EpisodeTable extends Slickable[EpisodeTable] {
  private[db] val table = TableQuery[EpisodeTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.episodeId,
      _.seriesId,
      _.season,
      _.episode,
    ).addForeignKeys(
      _.seriesFk,
    )

  private[db] def seed(implicit ec: ExecutionContext): Option[DBIO[_]] =
    Option.empty
}
