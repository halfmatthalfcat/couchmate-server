package com.couchmate.data.db.dao

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{LineupTable, ProviderChannelTable}
import com.couchmate.data.models.{Airing, Lineup, ProviderChannel, Show}
import com.couchmate.external.gracenote.models.GracenoteAiring
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

  def getOrAddLineup(
    providerChannelId: Long,
    show: Show,
    airing: Airing,
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Lineup] =
    db.run(LineupDAO.getOrAddLineup(providerChannelId, show, airing))

  def getOrAddLineup$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[(Long, Show, Airing), Lineup, NotUsed] =
    Slick.flowWithPassThrough(
      (LineupDAO.getOrAddLineup _).tupled
    )

  def disableLineup(
    providerChannelId: Long,
    gracenoteAiring: GracenoteAiring,
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Lineup] =
    db.run(LineupDAO.disableLineup(providerChannelId, gracenoteAiring))
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
    ) { (lineupId: Long) => {
      for {
        _ <- LineupTable
          .table
          .filter(_.lineupId === lineupId)
          .update(lineup)
        updated <- LineupDAO.getLineup(lineupId)
      } yield updated.get
    }}.transactionally


  private[dao] def getOrAddLineup(
    providerChannelId: Long,
    show: Show,
    airing: Airing,
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Lineup] = (for {
    airingExists <- AiringDAO.getAiringByShowStartAndEnd(
      showId = show.showId.get,
      startTime = airing.startTime,
      endTime = airing.endTime,
    )
    a <- airingExists
      .map(DBIO.successful)
      .getOrElse(AiringDAO.upsertAiring(airing))
    lineupExists <- getLineupForProviderChannelAndAiring(
      providerChannelId,
      a.airingId.get
    )
    lineup <- lineupExists
        .fold(upsertLineup(Lineup(
          lineupId = None,
          providerChannelId = providerChannelId,
          airingId = a.airingId.get,
          active = true
        )))(DBIO.successful)
  } yield lineup).transactionally

  private[dao] def disableLineup(
    providerChannelId: Long,
    gracenoteAiring: GracenoteAiring,
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Lineup] =
    (for {
      showExists <- ShowDAO
        .getShowByExt(gracenoteAiring.program.rootId)
      show = showExists.get
      airingExists <- AiringDAO
        .getAiringByShowStartAndEnd(
          show.showId.get,
          gracenoteAiring.startTime,
          gracenoteAiring.endTime
        )
      airing = airingExists.get
      lineupExists <- getLineupForProviderChannelAndAiring(
        providerChannelId,
        airing.airingId.get
      )
      lineup = lineupExists.get
      updated <- upsertLineup(lineup.copy(
        active = false
      ))
    } yield updated).transactionally
}
