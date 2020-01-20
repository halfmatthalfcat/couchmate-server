package com.couchmate.data.schema

import java.util.UUID

import PgProfile.api._
import com.couchmate.data.models.Lineup
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

class LineupDAO(tag: Tag) extends Table[Lineup](tag, "lineup") {
  def providerChannelId: Rep[Long] = column[Long]("provider_channel_id")
  def airingId: Rep[UUID] = column[UUID]("airing_id", O.SqlType("uuid"))
  def replacedBy: Rep[Option[UUID]] = column[Option[UUID]]("replaced_by", O.SqlType("uuid"))
  def * = (
    providerChannelId,
    airingId,
    replacedBy
  ) <> ((Lineup.apply _).tupled, Lineup.unapply)

  def primaryKey = primaryKey(
    "lineup_pk",
    (providerChannelId, airingId)
  )

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
    ).addPrimaryKeys(
      _.primaryKey,
    ).addForeignKeys(
      _.providerChannelFk,
      _.airingFk,
    )

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
    getLineupForProviderChannelAndAiring(
      lineup.providerChannelId,
      lineup.airingId,
    ) flatMap {
      case None =>
        db.run((lineupTable returning lineupTable) += lineup)
      case Some(lineup) => for {
        _ <- db.run(lineupTable.filter { l =>
          l.providerChannelId === lineup.providerChannelId &&
            l.airingId === lineup.airingId
        }.update(lineup))
        l <- db.run(lineupTable.filter { l =>
          l.providerChannelId === lineup.providerChannelId &&
            l.airingId === lineup.airingId
        }.result.head)
      } yield l
    }
  }
}
