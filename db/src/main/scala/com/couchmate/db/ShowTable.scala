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

//  private[this] lazy val getShowCompiled = Compiled { (showId: Rep[Long]) =>
//    showTable.filter(_.showId === showId)
//  }
//
//  def getShow(showId: Long): AppliedCompiledFunction[Long, Query[ShowTable, ShowTable, Seq], Seq[ShowTable]] = {
//    getShowCompiled(showId)
//  }
//
//  private[this] lazy val getShowFromExtCompiled = Compiled { (extId: Rep[Long]) =>
//    showTable.filter(_.extId === extId)
//  }
//
//  def getShowFromExt(extId: Long): AppliedCompiledFunction[Long, Query[ShowTable, ShowTable, Seq], Seq[ShowTable]] = {
//    getShowFromExtCompiled(extId)
//  }
//
//  def upsertShow(s: ShowTable): SqlStreamingAction[Vector[ShowTable], ShowTable, Effect] = {
//    sql"""
//         INSERT INTO show
//         (show_id, ext_id, type, episode_id, sport_event_id, title, description, original_air_date)
//         VALUES
//         (${s.showId}, ${s.extId}, ${s.`type`}, ${s.episodeId}, ${s.sportEventId}, ${s.title}, ${s.description}, ${s.originalAirDate})
//         ON CONFLICT (show_id)
//         DO UPDATE SET
//            ext_id = ${s.extId},
//            type = ${s.`type`},
//            episode_id = ${s.episodeId},
//            sport_event_id = ${s.sportEventId},
//            title = ${s.title},
//            description = ${s.description},
//            original_air_date = ${s.originalAirDate}
//         RETURNING *
//       """.as[ShowTable]
//  }
}
