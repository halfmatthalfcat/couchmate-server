package com.couchmate.migration.migrations

import java.time.LocalDateTime

import com.couchmate.common.tables.AiringTable
import com.couchmate.migration.db.MigrationItem
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.Airing
import slick.dbio.Effect
import slick.sql.FixedSqlAction

import scala.concurrent.ExecutionContext
import scala.util.Random

object AiringMigrations {

  val init = MigrationItem(13L, AiringTable.table)(
    _.create.addColumns(
      _.airingId,
      _.showId,
      _.startTime,
      _.endTime,
      _.duration,
    ).addForeignKeys(
      _.showFk,
    ).addIndexes(
      _.showStartTimeIdx,
      _.startTimeIdx,
      _.endTimeIdx,
    )
  )()

  private[this] def backfillShortcodes(
    implicit
    ec: ExecutionContext
  ) = for {
    rows <- AiringTable.table.result
    updates <- DBIO.sequence(rows.map(row => AiringTable
      .table
      .filter(_.airingId === row.airingId)
      .map(_.shortCode)
      .update(Some(Airing.generateShortcode))))
  } yield updates


  def addShortcodeSupport(implicit ec: ExecutionContext) = MigrationItem(27L, AiringTable.table)(
    _.addColumns(
      _.shortCode
    ).addIndexes(
      _.shortcodeIdx
    )
  )()

}
