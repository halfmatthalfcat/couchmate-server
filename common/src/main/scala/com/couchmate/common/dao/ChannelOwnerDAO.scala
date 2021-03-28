package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.ChannelOwner
import com.couchmate.common.tables.ChannelOwnerTable
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object ChannelOwnerDAO {
  private[this] lazy val getChannelOwnerQuery = Compiled { (channelOwnerId: Rep[Long]) =>
    ChannelOwnerTable.table.filter(_.channelOwnerId === channelOwnerId)
  }

  def getChannelOwner(channelOwnerId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[ChannelOwner]] = cache(
    "getChannelOwner", channelOwnerId
  )(db.run(getChannelOwnerQuery(channelOwnerId).result.headOption))()

  private[this] lazy val getChannelOwnerForExtQuery = Compiled { (extId: Rep[Long]) =>
    ChannelOwnerTable.table.filter(_.extId === extId)
  }

  def getChannelOwnerForExt(extId: Long)(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[ChannelOwner]] = cache(
    "getChannelOwnerForExt", extId
  )(db.run(getChannelOwnerForExtQuery(extId).result.headOption))()

  private[this] def addChannelOwnerForId(co: ChannelOwner)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addChannelOwnerForId", co.extId
  )(db.run(
    sql"SELECT insert_or_get_channel_owner_id(${co.channelOwnerId}, ${co.extId}, ${co.callsign})"
      .as[Long].head
  ))()

  def addOrGetChannelOwner(channelOwner: ChannelOwner)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[ChannelOwner] = cache(
    "addOrGetChannelOwner", channelOwner.extId
  )(for {
    exists <- getChannelOwnerForExt(channelOwner.extId)()
    co <- exists.fold(for {
      _ <- addChannelOwnerForId(channelOwner)
      selected <- getChannelOwnerForExt(channelOwner.extId)(bust = true)
        .map(_.get)
    } yield selected)(Future.successful)
  } yield co)()
}
