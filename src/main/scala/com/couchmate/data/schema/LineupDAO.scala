package com.couchmate.data.schema

import java.util.UUID

import PgProfile.api._
import com.couchmate.data.models.Lineup
import slick.lifted.{AppliedCompiledFunction, PrimaryKey, Tag}
import slick.migration.api.TableMigration
import slick.sql.SqlStreamingAction

import scala.concurrent.{ExecutionContext, Future}

class LineupDAO(tag: Tag) extends Table[Lineup](tag, "lineup") {
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
    ProviderChannelDAO.providerChannelTable,
  )(
    _.providerChannelId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def airingFk = foreignKey(
    "lineup_airing_fk",
    airingId,
    AiringDAO.airingTable,
  )(
    _.airingId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object LineupDAO {
  val lineupTable = TableQuery[LineupDAO]

  val init = TableMigration(lineupTable)
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

  private[this] lazy val getLineupCompiled = Compiled { (lineupId: Rep[Long]) =>
    lineupTable.filter(_.lineupId === lineupId)
  }

  def getLineup(lineupId: Long): AppliedCompiledFunction[Long, Query[LineupDAO, Lineup, Seq], Seq[Lineup]] = {
    getLineupCompiled(lineupId)
  }

  def getLineupForProviderChannelAndAiringCompiled = Compiled { (providerChannelId: Rep[Long], airingId: Rep[UUID]) =>
    lineupTable.filter { lineup =>
      lineup.providerChannelId === providerChannelId &&
      lineup.airingId === airingId
    }
  }

  def getLineupForProviderChannelAndAiring(providerChannelId: Long, airingId: UUID): AppliedCompiledFunction[(Long, UUID), Query[LineupDAO, Lineup, Seq], Seq[Lineup]] = {
    getLineupForProviderChannelAndAiringCompiled(providerChannelId, airingId)
  }

  private[this] lazy val lineupsExistForProviderCompiled = Compiled { (providerId: Rep[Long]) =>
    for {
      l <- lineupTable
      pc <- ProviderChannelDAO.providerChannelTable
      if  l.providerChannelId === pc.providerChannelId &&
        pc.providerId === providerId
    } yield pc.providerId
  }

  def lineupsExistForProvider(providerId: Long): AppliedCompiledFunction[Long, Query[Rep[Long], Long, Seq], Seq[Long]] = {
    lineupsExistForProviderCompiled(providerId)
  }

  def upsertLineup(l: Lineup): SqlStreamingAction[Vector[Lineup], Lineup, Effect] = {
    sql"""
         INSERT INTO lineup
         (lineup_id, provider_channel_id, airing_id, replaced_by)
         VALUES
         (${l.lineupId}, ${l.providerChannelId}, ${l.airingId}, ${l.replacedBy})
         ON CONFLICT (lineup_id)
         DO UPDATE SET
            provider_channel_id = ${l.providerChannelId},
            airing_id = ${l.airingId},
            replaced_by = ${l.replacedBy}
         RETURNING *
       """.as[Lineup]
  }
}
