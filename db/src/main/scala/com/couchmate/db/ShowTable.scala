package com.couchmate.db

import java.time.LocalDateTime

import com.couchmate.common.models.Show
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class ShowTable(tag: Tag) extends Table[Show](tag, "show") {
  def showId: Rep[Long] = column[Long]("show_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id")
  def `type`: Rep[String] = column[String]("type")
  def episodeId: Rep[Option[Long]] = column[Option[Long]]("episode_id")
  def sportEventId: Rep[Option[Long]] = column[Option[Long]]("sport_event_id")
  def title: Rep[String] = column[String]("title")
  def description: Rep[String] = column[String]("description")
  def originalAirDate: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("original_air_date", O.SqlType("timestamp"))
  def * = (
    showId.?,
    extId,
    `type`,
    episodeId,
    sportEventId,
    title,
    description,
    originalAirDate,
  ) <> ((Show.apply _).tupled, Show.unapply)

  def episodeFk = foreignKey(
    "show_episode_fk",
    episodeId,
    EpisodeTable.table,
    )(
    _.episodeId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def sportFk = foreignKey(
    "sport_episode_fk",
    sportEventId,
    SportEventTable.table,
    )(
    _.sportEventId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object ShowTable extends Slickable[ShowTable] {
  val table = TableQuery[ShowTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.showId,
      _.extId,
      _.`type`,
      _.episodeId,
      _.sportEventId,
      _.title,
      _.description,
      _.originalAirDate,
    ).addForeignKeys(
      _.episodeFk,
      _.sportFk,
    )
}
