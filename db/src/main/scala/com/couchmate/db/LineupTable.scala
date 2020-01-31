package com.couchmate.db

import java.util.UUID

import com.couchmate.common.models.Lineup
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class LineupTable(tag: Tag) extends Table[Lineup](tag, "lineup") {
  def lineupId: Rep[Long] = column[Long]("lineup_id", O.AutoInc, O.PrimaryKey)
  def providerChannelId: Rep[Long] = column[Long]("provider_channel_id")
  def airingId: Rep[UUID] = column[UUID]("airing_id", O.SqlType("uuid"))
  def replacedBy: Rep[Option[UUID]] = column[Option[UUID]]("replaced_by", O.SqlType("uuid"))
  def * = (
    lineupId.?,
    providerChannelId,
    airingId,
    replacedBy,
  ) <> ((Lineup.apply _).tupled, Lineup.unapply)

  def providerChannelFk = foreignKey(
    "lineup_provider_channel_fk",
    providerChannelId,
    ProviderChannelTable.table,
    )(
    _.providerChannelId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def airingFk = foreignKey(
    "lineup_airing_fk",
    airingId,
    AiringTable.table,
    )(
    _.airingId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object LineupTable extends Slickable[LineupTable] {
  val table = TableQuery[LineupTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.lineupId,
      _.providerChannelId,
      _.airingId,
      _.replacedBy,
    ).addForeignKeys(
      _.providerChannelFk,
      _.airingFk,
    )

//  private[this] lazy val getLineupCompiled = Compiled { (lineupId: Rep[Long]) =>
//    lineupTable.filter(_.lineupId === lineupId)
//  }
//
//  def getLineup(lineupId: Long): AppliedCompiledFunction[Long, Query[LineupTable, LineupTable, Seq], Seq[LineupTable]] = {
//    getLineupCompiled(lineupId)
//  }
//
//  def getLineupForProviderChannelAndAiringCompiled = Compiled { (providerChannelId: Rep[Long], airingId: Rep[UUID]) =>
//    lineupTable.filter { lineup =>
//      lineup.providerChannelId === providerChannelId &&
//      lineup.airingId === airingId
//    }
//  }
//
//  def getLineupForProviderChannelAndAiring(providerChannelId: Long, airingId: UUID): AppliedCompiledFunction[(Long, UUID), Query[LineupTable, LineupTable, Seq], Seq[LineupTable]] = {
//    getLineupForProviderChannelAndAiringCompiled(providerChannelId, airingId)
//  }
//
//  private[this] lazy val lineupsExistForProviderCompiled = Compiled { (providerId: Rep[Long]) =>
//    for {
//      l <- lineupTable
//      pc <- ProviderChannelTable.providerChannelTable
//      if  l.providerChannelId === pc.providerChannelId &&
//        pc.providerId === providerId
//    } yield pc.providerId
//  }
//
//  def lineupsExistForProvider(providerId: Long): AppliedCompiledFunction[Long, Query[Rep[Long], Long, Seq], Seq[Long]] = {
//    lineupsExistForProviderCompiled(providerId)
//  }
//
//  def upsertLineup(l: LineupTable): SqlStreamingAction[Vector[LineupTable], LineupTable, Effect] = {
//    sql"""
//         INSERT INTO lineup
//         (lineup_id, provider_channel_id, airing_id, replaced_by)
//         VALUES
//         (${l.lineupId}, ${l.providerChannelId}, ${l.airingId}, ${l.replacedBy})
//         ON CONFLICT (lineup_id)
//         DO UPDATE SET
//            provider_channel_id = ${l.providerChannelId},
//            airing_id = ${l.airingId},
//            replaced_by = ${l.replacedBy}
//         RETURNING *
//       """.as[LineupTable]
//  }
}
