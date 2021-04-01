package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.Channel
import com.couchmate.common.tables.{ChannelTable, LineupTable, ProviderChannelTable}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object ChannelDAO {
  private[this] lazy val getChannelQuery = Compiled { (channelId: Rep[Long]) =>
    ChannelTable.table.filter(_.channelId === channelId)
  }

  def getChannel(channelId: Long)(bust: Boolean = true)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Channel]] = cache(
    "getChannel", channelId
  )(db.run(getChannelQuery(channelId).result.headOption))(
    bust = bust
  )

  private[this] lazy val getChannelForExtQuery = Compiled { (extId: Rep[Long]) =>
    ChannelTable.table.filter(_.extId === extId)
  }

  def getChannelForExt(extId: Long)(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Channel]] = cache(
    "getChannelForExt", extId
  )(db.run(getChannelForExtQuery(extId).result.headOption))(
    bust = bust
  )

  private[this] lazy val getChannelForProviderChannelQuery = Compiled {
    (providerChannelId: Rep[Long]) => for {
      pc <- ProviderChannelTable.table if pc.providerChannelId === providerChannelId
      c <- ChannelTable.table if c.channelId === pc.channelId
    } yield c
  }

  def getChannelForProviderChannel(providerChannelId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Channel]] = cache(
    "getChannelForProviderChannel", providerChannelId
  )(db.run(getChannelForProviderChannelQuery(providerChannelId).result.headOption))()

  private[this] lazy val getChannelForProviderAndAiringQuery = Compiled {
    (providerId: Rep[Long], airingId: Rep[String]) => for {
      l <- LineupTable.table if l.airingId === airingId
      pc <- ProviderChannelTable.table if (
        pc.providerChannelId === l.providerChannelId &&
        pc.providerId === providerId
      )
      c <- ChannelTable.table if c.channelId === pc.channelId
    } yield c
  }

  def getChannelForProviderAndAiring(providerId: Long, airingId: String)(
    implicit
    ec: ExecutionContext,
    db: Database,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Channel]] =
    cache(
      "getChannelForProviderAndAiring",
      providerId,
      airingId,
    )(db.run(getChannelForProviderAndAiringQuery(providerId, airingId).result.headOption))()

  private[this] def addChannelForId(c: Channel)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addChannelForId",
    c.extId
  )(db.run(
    sql"SELECT insert_or_get_channel_id(${c.channelId}, ${c.extId}, ${c.channelOwnerId}, ${c.callsign})"
      .as[Long].head
  ))()

  def addOrGetChannel(channel: Channel)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Channel] = cache(
    "addOrGetChannel",
    channel.extId
  )(for {
    exists <- getChannelForExt(channel.extId)()
    c <- exists.fold(for {
      _ <- addChannelForId(channel)
      selected <- getChannelForExt(channel.extId)(bust = true)
        .map(_.get)
    } yield selected)(Future.successful)
  } yield c)()
}
