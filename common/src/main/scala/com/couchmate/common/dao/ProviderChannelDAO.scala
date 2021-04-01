package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{Channel, ChannelOwner, ProviderChannel}
import com.couchmate.common.tables.{LineupTable, ProviderChannelTable}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object ProviderChannelDAO {
  private[this] lazy val getProviderChannelQuery = Compiled { (providerChannelId: Rep[Long]) =>
    ProviderChannelTable.table.filter(_.providerChannelId === providerChannelId)
  }

  def getProviderChannel(providerChannelId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[ProviderChannel]] = cache(
    "getProviderChannel",
    providerChannelId
  )(db.run(getProviderChannelQuery(providerChannelId).result.headOption))()

  private[this] lazy val getProviderChannelForProviderAndChannelQuery = Compiled {
    (providerId: Rep[Long], channelId: Rep[Long]) =>
      ProviderChannelTable.table.filter { pc =>
        pc.providerId === providerId &&
        pc.channelId === channelId
      }
  }

  def getProviderChannelForProviderAndChannel(
    providerId: Long,
    channelId: Long
  )(
    bust: Boolean = false
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[ProviderChannel]] = cache(
    "getProviderChannelForProviderAndChannel",
    providerId,
    channelId
  )(db.run(getProviderChannelForProviderAndChannelQuery(
    providerId,
    channelId
  ).result.headOption))(bust = bust)

  private[this] lazy val getProviderChannelForProviderAndAiringQuery = Compiled {
    (providerId: Rep[Long], airingId: Rep[String]) => for {
      l <- LineupTable.table if l.airingId === airingId
      pc <- ProviderChannelTable.table if (
        pc.providerChannelId === l.providerChannelId &&
        pc.providerId === providerId
      )
    } yield pc
  }

  def getProviderChannelForProviderAndAiring(
    providerId: Long,
    airingId: String
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[ProviderChannel]] = cache(
    "getProviderChannelForProviderAndAiring",
    providerId,
    airingId
  )(db.run(getProviderChannelForProviderAndAiringQuery(
    providerId,
    airingId
  ).result.headOption))()

  private[this] def addProviderChannelForId(p: ProviderChannel)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addProviderChannelForId",
    p.providerId,
    p.channelId
  )(db.run(
    sql"SELECT insert_or_get_provider_channel_id(${p.providerChannelId}, ${p.providerId}, ${p.channelId}, ${p.channel})"
      .as[Long].head
  ))()

  def addOrGetProviderChannel(p: ProviderChannel)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[ProviderChannel] = cache(
    "addOrGetProviderChannel",
    p.providerId,
    p.channelId
  )(for {
    exists <- getProviderChannelForProviderAndChannel(
      p.providerId, p.channelId
    )()
    pc <- exists.fold(for {
      _ <- addProviderChannelForId(p)
      selected <- getProviderChannelForProviderAndChannel(
        p.providerId, p.channelId
      )(bust = true).map(_.get)
    } yield selected)(Future.successful)
  } yield pc)()

  def addOrGetChannel(
    providerId: Long,
    channelNumber: String,
    channelOwner: Option[ChannelOwner],
    channel: Channel
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[ProviderChannel] = (for {
    cO <- channelOwner
      .fold[Future[Option[ChannelOwner]]](
        Future.successful(Option.empty[ChannelOwner])
      )(owner =>
        ChannelOwnerDAO
          .addOrGetChannelOwner(owner)
          .map(o => Option(o))
      )
    c <- cO.fold(ChannelDAO.addOrGetChannel(channel))(owner =>
      ChannelDAO.addOrGetChannel(channel.copy(
        channelOwnerId = owner.channelOwnerId
      )))
    pc <- addOrGetProviderChannel(ProviderChannel(
      providerChannelId = None,
      providerId = providerId,
      channelId = c.channelId.get,
      channel = channelNumber
    ))
  } yield pc)
}
