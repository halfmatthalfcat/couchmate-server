package com.couchmate.util

import com.couchmate.common.dao.{ChannelDAO, GridDAO, RoomActivityAnalyticsDAO, SeriesDAO, UserActivityAnalyticsDAO}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.util.mail.AnalyticsReport
import com.couchmate.util.mail.Fragments.{banner, email, row}
import com.github.benmanes.caffeine.cache.Cache
import com.github.blemale.scaffeine.Scaffeine
import play.api.libs.json.Json
import redis.clients.jedis._
import scalacache.Entry
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache
import scalatags.Text.all._

import java.time.format.{DateTimeFormatter, FormatStyle}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

object DbQueryTester {
  def main(args: Array[String]): Unit = {
    import scalacache.serialization.binary._

    implicit val db: Database = Database.forConfig("db")
    implicit val jedisPool: JedisPool = new JedisPool()

    val caffeineCache: Cache[String, Entry[String]] = Scaffeine()
      .build[String, Entry[String]]().underlying

    implicit val caffeine: CaffeineCache[String] =
      CaffeineCache(caffeineCache)

    implicit val redis: RedisCache[String] =
      RedisCache(jedisPool)

    println(
      Await.result(
        SeriesDAO.getSomeGridSeries(Seq(1L, 2L)),
        Duration.Inf
      )
    )
  }
}
