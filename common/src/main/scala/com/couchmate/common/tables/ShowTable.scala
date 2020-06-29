package com.couchmate.common.tables

import java.time.LocalDateTime

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{Show, ShowType}
import com.couchmate.common.util.slick.WithTableQuery

class ShowTable(tag: Tag) extends Table[Show](tag, "show") {
  def showId: Rep[Long] = column[Long]("show_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id")
  def `type`: Rep[ShowType] = column[ShowType]("type")
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

object ShowTable extends WithTableQuery[ShowTable] {
  private[couchmate] val table = TableQuery[ShowTable]
}
