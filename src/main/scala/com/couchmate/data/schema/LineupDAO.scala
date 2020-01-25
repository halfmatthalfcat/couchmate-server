package com.couchmate.data.schema

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.Lineup
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class LineupDAO(tag: Tag) extends Table[Lineup](tag, "lineup") {
  def lineupId: Rep[Long] = column[Long]("lineup_id", O.PrimaryKey, O.AutoInc)
  def providerChannelId: Rep[Long] = column[Long]("provider_channel_id")
  def airingId: Rep[UUID] = column[UUID]("airing_id", O.SqlType("uuid"))
  def replacedBy: Rep[UUID] = column[UUID]("replaced_by", O.SqlType("uuid"))
  def * = (
    lineupId.?,
    providerChannelId,
    airingId,
    replacedBy.?,
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
      _.providerChannelId,
      _.airingId,
      _.replacedBy,
    ).addForeignKeys(
      _.providerChannelFk,
      _.airingFk,
    )

  def getLineup(lineupId: Long)(
    implicit
    session: SlickSession,
  ): Flow[Long, Option[Lineup], NotUsed] = Slick.flowWithPassThrough { lineupId =>
    lineupTable.filter(_.lineupId === lineupId).result.headOption
  }

  def getLineupForProviderChannelAndAiring()(
    implicit
    session: SlickSession,
  ): Flow[(Long, UUID), Option[Lineup], NotUsed] = Slick.flowWithPassThrough {
    case (providerChannelId, airingId) => lineupTable.filter { lineup =>
      lineup.providerChannelId === providerChannelId &&
      lineup.airingId === airingId
    }.result.headOption
  }

  def lineupsExistForProvider()(
    implicit
    session: SlickSession,
  ): Flow[Long, Boolean, NotUsed] = Slick.flowWithPassThrough { providerId =>
    (for {
      l <- lineupTable
      pc <- ProviderChannelDAO.providerChannelTable
      if  l.providerChannelId === pc.providerChannelId &&
          pc.providerId === providerId
    } yield pc.providerId).distinct.exists.result
  }

  def upsertLineup()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[Lineup, Lineup, NotUsed] = Slick.flowWithPassThrough {
    case lineup @ Lineup(None, _, _, _) =>
      (lineupTable returning lineupTable) += lineup
    case lineup @ Lineup(Some(lineupId), _, _, _) => for {
      _ <- lineupTable.filter(_.lineupId === lineupId).update(lineup)
      l <- lineupTable.filter(_.lineupId === lineupId).result.head
    } yield l
  }
}
