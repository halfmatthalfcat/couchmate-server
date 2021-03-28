package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{Airing, Lineup}
import com.couchmate.common.models.thirdparty.gracenote.GracenoteAiring
import com.couchmate.common.tables.{LineupTable, ProviderChannelTable}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object LineupDAO {
  private[this] lazy val getLineupQuery = Compiled { (lineupId: Rep[Long]) =>
    LineupTable.table.filter(_.lineupId === lineupId)
  }

  def getLineup(lineupId: Long)(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Lineup]] = cache(
    "getLineup",
    lineupId
  )(db.run(getLineupQuery(lineupId).result.headOption))(bust = bust)

  private[this] lazy val lineupsExistForProviderQuery = Compiled { (providerId: Rep[Long]) =>
    (for {
      l <- LineupTable.table
      pc <- ProviderChannelTable.table if (
        l.providerChannelId === pc.providerChannelId &&
        pc.providerId === providerId
      )
    } yield pc.providerId).exists
  }

  def lineupsExistForProvider(providerId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Boolean] = cache(
    "lineupsExistForProvider",
    providerId
  )(db.run(lineupsExistForProviderQuery(providerId).result))()

  private[this] lazy val getLineupForProviderChannelAndAiringQuery = Compiled {
    (providerChannelId: Rep[Long], airingId: Rep[String]) =>
      LineupTable.table.filter { l =>
        l.providerChannelId === providerChannelId &&
        l.airingId === airingId
      }
  }

  def getLineupForProviderChannelAndAiring(
    providerChannelId: Long,
    airingId: String
  )(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Lineup]] = cache(
    "getLineupForProviderChannelAndAiring",
    providerChannelId,
    airingId
  )(db.run(getLineupForProviderChannelAndAiringQuery(
    providerChannelId,
    airingId
  ).result.headOption))(bust = bust)

  private[this] def addLineupForId(l: Lineup)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addLineupForId",
    l.providerChannelId,
    l.airingId
  )(db.run(
    sql"""SELECT insert_or_get_lineup_id(${l.providerChannelId}, ${l.airingId}, ${l.active})"""
      .as[Long].head
  ))()

  def addOrGetLineup(l: Lineup)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Lineup] = cache(
    "addOrGetLineup",
    l.providerChannelId,
    l.airingId
  )(for {
    exists <- getLineupForProviderChannelAndAiring(
      l.providerChannelId, l.airingId
    )()
    l <- exists.fold(for {
      _ <- addLineupForId(l)
      selected <- getLineupForProviderChannelAndAiring(
        l.providerChannelId, l.airingId
      )(bust = true).map(_.get)
    } yield selected)(Future.successful)
  } yield l)()

  def addOrGetLineupFromProviderChannelAndAiring(
    providerChannelId: Long,
    airing: Airing,
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Lineup] = cache(
    "addOrGetLineupFromProviderChannelAndAiring",
    providerChannelId,
    airing.airingId
  )(for {
    a <- AiringDAO.addOrGetAiring(airing)
    l <- addOrGetLineup(Lineup(
      lineupId = None,
      providerChannelId = providerChannelId,
      airingId = a.airingId,
      active = true
    ))
  } yield l)()

  def disableLineup(
    providerChannelId: Long,
    gracenoteAiring: GracenoteAiring,
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Lineup]] =
    (for {
      s <- ShowDAO.getShowByExt(gracenoteAiring.program.rootId)()
      a <- s.fold(Future.successful(Option.empty[Airing]))(show =>
        AiringDAO
          .getAiringByShowStartAndEnd(
            show.showId.get,
            gracenoteAiring.startTime,
            gracenoteAiring.endTime
          )
      )
      l <- a.fold(Future.successful(Option.empty[Lineup]))(airing =>
        getLineupForProviderChannelAndAiring(
          providerChannelId,
          airing.airingId
        )()
      )
      updated <- l.fold(Future.successful(Option.empty[Lineup]))(lineup => for {
        _ <- db.run(LineupTable
          .table
          .filter(_.lineupId === lineup.lineupId)
          .map(_.active)
          .update(false)
        )
        newL <- getLineup(lineup.lineupId.get)(bust = true)
      } yield newL)
    } yield updated)
}
