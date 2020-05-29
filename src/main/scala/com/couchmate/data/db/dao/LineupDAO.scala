package com.couchmate.data.db.dao

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{LineupTable, ProviderChannelTable}
import com.couchmate.data.models.{Airing, Lineup, ProviderChannel}
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

trait LineupDAO {

  def getLineup(lineupId: Long)(
    implicit
    db: Database
  ): Future[Option[Lineup]] =
    db.run(LineupDAO.getLineup(lineupId))

  def getLineup$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Lineup], NotUsed] =
    Slick.flowWithPassThrough(LineupDAO.getLineup)

  def lineupsExistForProvider(providerId: Long)(
    implicit
    db: Database
  ): Future[Boolean] =
    db.run(LineupDAO.lineupsExistForProvider(providerId))

  def lineupsExistForProvider$()(
    implicit
    session: SlickSession
  ): Flow[Long, Boolean, NotUsed] =
    Slick.flowWithPassThrough(LineupDAO.lineupsExistForProvider)

  def getLineupForProviderChannelAndAiring(
    providerChannelId: Long,
    airingId: UUID
  )(
    implicit
    db: Database
  ): Future[Option[Lineup]] =
    db.run(LineupDAO.getLineupForProviderChannelAndAiring(providerChannelId, airingId))

  def getLineupForProviderChannelAndAiring$()(
    implicit
    session: SlickSession
  ): Flow[(Long, UUID), Option[Lineup], NotUsed] =
    Slick.flowWithPassThrough(
      (LineupDAO.getLineupForProviderChannelAndAiring _).tupled
    )

  def upsertLineup(lineup: Lineup)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Lineup] =
    db.run(LineupDAO.upsertLineup(lineup))

  def upsertLineup$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[Lineup, Lineup, NotUsed] =
    Slick.flowWithPassThrough(LineupDAO.upsertLineup)
}

object LineupDAO {
  private[this] lazy val getLineupQuery = Compiled { (lineupId: Rep[Long]) =>
    LineupTable.table.filter(_.lineupId === lineupId)
  }

  private[dao] def getLineup(lineupId: Long): DBIO[Option[Lineup]] =
    getLineupQuery(lineupId).result.headOption

  private[this] lazy val lineupsExistForProviderQuery = Compiled { (providerId: Rep[Long]) =>
    (for {
      l <- LineupTable.table
      pc <- ProviderChannelTable.table if (
        l.providerChannelId === pc.providerChannelId &&
        pc.providerId === providerId
      )
    } yield pc.providerId).exists
  }

  private[dao] def lineupsExistForProvider(providerId: Long): DBIO[Boolean] =
    lineupsExistForProviderQuery(providerId).result

  private[this] lazy val getLineupForProviderChannelAndAiringQuery = Compiled {
    (providerChannelId: Rep[Long], airingId: Rep[UUID]) =>
      LineupTable.table.filter { l =>
        l.providerChannelId === providerChannelId &&
        l.airingId === airingId
      }
  }

  private[dao] def getLineupForProviderChannelAndAiring(
    providerChannelId: Long,
    airingId: UUID
  ): DBIO[Option[Lineup]] =
    getLineupForProviderChannelAndAiringQuery(
      providerChannelId,
      airingId
    ).result.headOption

  private[dao] def upsertLineup(lineup: Lineup)(
    implicit
    ec: ExecutionContext
  ): DBIO[Lineup] =
    lineup.lineupId.fold[DBIO[Lineup]](
      (LineupTable.table returning LineupTable.table) += lineup
    ) { (lineupId: Long) => for {
      _ <- LineupTable.table.update(lineup)
      updated <- LineupDAO.getLineup(lineupId)
    } yield updated.get}
}
