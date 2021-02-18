package com.couchmate.common.dao

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{Airing, Lineup, Show}
import com.couchmate.common.models.thirdparty.gracenote.GracenoteAiring
import com.couchmate.common.tables.{LineupTable, ProviderChannelTable}

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
    airingId: String
  )(
    implicit
    db: Database
  ): Future[Option[Lineup]] =
    db.run(LineupDAO.getLineupForProviderChannelAndAiring(providerChannelId, airingId))

  def getLineupForProviderChannelAndAiring$()(
    implicit
    session: SlickSession
  ): Flow[(Long, String), Option[Lineup], NotUsed] =
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
    airing: Airing,
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Lineup] = for {
    a <- db.run(AiringDAO.addAndGetAiring(airing))
    l <- db.run(LineupDAO.addAndGetLineup(Lineup(
      lineupId = None,
      providerChannelId = providerChannelId,
      airingId = a.airingId.get,
      active = true
    )))
  } yield l

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

  private[common] def getLineup(lineupId: Long): DBIO[Option[Lineup]] =
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

  private[common] def lineupsExistForProvider(providerId: Long): DBIO[Boolean] =
    lineupsExistForProviderQuery(providerId).result

  private[this] lazy val getLineupForProviderChannelAndAiringQuery = Compiled {
    (providerChannelId: Rep[Long], airingId: Rep[String]) =>
      LineupTable.table.filter { l =>
        l.providerChannelId === providerChannelId &&
        l.airingId === airingId
      }
  }

  private[common] def getLineupForProviderChannelAndAiring(
    providerChannelId: Long,
    airingId: String
  ): DBIO[Option[Lineup]] =
    getLineupForProviderChannelAndAiringQuery(
      providerChannelId,
      airingId
    ).result.headOption

  private[common] def upsertLineup(lineup: Lineup)(
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

  private[this] def addLineupForId(l: Lineup) =
    sql"""
          WITH row AS (
            INSERT INTO lineup
            (provider_channel_id, airing_id, active)
            VALUES
            (${l.providerChannelId}, ${l.airingId}, ${l.active})
            ON CONFLICT (provider_channel_id, airing_id)
            DO NOTHING
            RETURNING lineup_id
          ) SELECT lineup_id FROM row
            UNION SELECT lineup_id FROM lineup
            WHERE provider_channel_id = ${l.providerChannelId} AND
                  airing_id = ${l.airingId}
         """.as[Long]

  private[common] def addAndGetLineup(l: Lineup)(
    implicit
    ec: ExecutionContext
  ): DBIO[Lineup] = (for {
    lineupId <- addLineupForId(l).head
    lineup <- getLineupQuery(lineupId).result.head
  } yield lineup).transactionally

  private[common] def addOrGetLineup(l: Lineup) =
    sql"""
          WITH input_rows(provider_channel_id, airing_id, active) AS (
            VALUES (${l.providerChannelId}, ${l.airingId}, ${l.active})
          ), ins AS (
            INSERT INTO lineup as l (provider_channel_id, airing_id, active)
            SELECT * from input_rows
            ON CONFLICT (provider_channel_id, airing_id) DO NOTHING
            RETURNING lineup_id, provider_channel_id, airing_id, active
          ), sel AS (
            SELECT lineup_id, provider_channel_id, airing_id, active
            FROM ins
            UNION ALL
            SELECT l.lineup_id, provider_channel_id, airing_id, l.active
            FROM input_rows
            JOIN lineup as l USING (provider_channel_id, airing_id)
          ), ups AS (
           INSERT INTO lineup AS l (provider_channel_id, airing_id, active)
           SELECT i.*
           FROM   input_rows i
           LEFT   JOIN sel   s USING (provider_channel_id, airing_id)
           WHERE  s.provider_channel_id IS NULL
           ON     CONFLICT (provider_channel_id, airing_id) DO UPDATE
           SET    provider_channel_id = l.provider_channel_id,
                  airing_id = l.airing_id
           RETURNING lineup_id, provider_channel_id, airing_id, active
         )  SELECT lineup_id, provider_channel_id, airing_id, active FROM sel
            UNION  ALL
            TABLE  ups;
         """.as[Lineup]

  private[common] def disableLineup(
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
