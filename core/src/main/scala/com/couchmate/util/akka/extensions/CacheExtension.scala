package com.couchmate.util.akka.extensions

import akka.actor.typed.pubsub.Topic
import akka.actor.typed.pubsub.Topic.Publish
import akka.actor.typed.{ActorRef, ActorSystem, Extension, ExtensionId}
import com.couchmate.services.cache.ClusterCacheBuster.{Bust, Command}
import com.github.benmanes.caffeine.cache.Cache
import com.github.blemale.scaffeine.Scaffeine
import com.typesafe.config.{Config, ConfigFactory}
import redis.clients.jedis.{JedisPool, JedisPoolConfig}
import scalacache.Entry
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.util.Try

class CacheExtension(system: ActorSystem[_]) extends Extension {
  import scalacache.serialization.binary._

  private[this] val config: Config = ConfigFactory.load()

  private[this] val caffeineCache: Cache[String, Entry[String]] = Scaffeine()
    .weigher((_: String, value: Entry[String]) => value.value.getBytes("UTF-8").length)
    .maximumWeight(config.getBytes("cache.inMemory.maxSize"))
    .build[String, Entry[String]]().underlying

  private[this] val jedisPoolConfig = new JedisPoolConfig()
  jedisPoolConfig.setMaxTotal(128)
  jedisPoolConfig.setMaxIdle(128)
  jedisPoolConfig.setMaxIdle(16)
  jedisPoolConfig.setTestOnBorrow(true)
  jedisPoolConfig.setTestOnReturn(true)
  jedisPoolConfig.setTestWhileIdle(true)

  private[this] val jedisPool: JedisPool = new JedisPool(
    jedisPoolConfig,
    config.getString("cache.redis.host"),
    config.getInt("cache.redis.port"),
    10000,
    Try(config.getString("cache.redis.password")).getOrElse(null),
    config.getString("environment") != "local"
  )

  System.out.println(s"${config.getString("cache.redis.host")}:${config.getInt("cache.redis.port")} @ ${Try(config.getString("cache.redis.password")).getOrElse(null)}")

  implicit val caffeine: CaffeineCache[String] =
    CaffeineCache(caffeineCache)

  implicit val redis: RedisCache[String] =
    RedisCache(jedisPool)

  val cacheTopic: ActorRef[Topic.Command[Command]] = system.systemActorOf(
    Topic[Command]("cache"),
    "cache"
  )

  def clusterBust(cacheKey: String): Unit =
    cacheTopic ! Publish(Bust(cacheKey))
}

object CacheExtension extends ExtensionId[CacheExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): CacheExtension = new CacheExtension(system)
}