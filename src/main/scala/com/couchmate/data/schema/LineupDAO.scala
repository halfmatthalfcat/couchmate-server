package com.couchmate.data.schema

import java.util.UUID

import PgProfile.api._
import com.couchmate.data.models.Lineup
import slick.lifted.{PrimaryKey, Tag}
import slick.migration.api.TableMigration

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

  def getLineup(lineupId: Long)(
    implicit
    db: Database,
  ): Future[Option[Lineup]] = {
    db.run(lineupTable.filter(_.lineupId === lineupId).result.headOption)
  }

  def getLineupForProviderChannelAndAiring(providerChannelId: Long, airingId: UUID)(
    implicit
    db: Database,
  ): Future[Option[Lineup]] = {
    db.run(lineupTable.filter { lineup =>
      lineup.providerChannelId === providerChannelId &&
      lineup.airingId === airingId
    }.result.headOption)
  }

  def lineupsExistForProvider(providerId: Long)(
    implicit
    db: Database,
  ): Future[Boolean] = {
    db.run((for {
      l <- lineupTable
      pc <- ProviderChannelDAO.providerChannelTable
        if  l.providerChannelId === pc.providerChannelId &&
            pc.providerId === providerId
    } yield pc.providerId).distinct.exists.result)
  }

  def upsertLineup(lineup: Lineup)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Lineup] = {
    lineup match {
      case Lineup(None, _, _, _) =>
        db.run((lineupTable returning lineupTable) += lineup)
      case Lineup(Some(lineupId), _, _, _) => for {
        _ <- db.run(lineupTable.filter(_.lineupId === lineupId).update(lineup))
        l <- db.run(lineupTable.filter(_.lineupId === lineupId).result.head)
      } yield l
    }
  }
}
