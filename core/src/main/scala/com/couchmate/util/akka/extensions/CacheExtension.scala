package com.couchmate.util.akka.extensions

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.github.benmanes.caffeine.cache.Cache
import com.github.blemale.scaffeine.Scaffeine
import com.typesafe.config.{Config, ConfigFactory}
import redis.clients.jedis.JedisPool
import scalacache.Entry
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

class CacheExtension(system: ActorSystem[_]) extends Extension {
  import scalacache.serialization.binary._

  private[this] val config: Config = ConfigFactory.load()

  private[this] val caffeineCache: Cache[String, Entry[String]] = Scaffeine()
    .weigher((_: String, value: Entry[String]) => value.value.getBytes("UTF-8").length)
    .maximumWeight(config.getBytes("cache.inMemory.maxSize"))
    .build[String, Entry[String]]().underlying

  private[this] val jedisPool: JedisPool = new JedisPool(
    config.getString("cache.redis.host"),
    config.getInt("cache.redis.port")
  )

  implicit val caffeine: CaffeineCache[String] =
    CaffeineCache(caffeineCache)

  implicit val redis: RedisCache[String] =
    RedisCache(jedisPool)
}

object CacheExtension extends ExtensionId[CacheExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): CacheExtension = new CacheExtension(system)
}