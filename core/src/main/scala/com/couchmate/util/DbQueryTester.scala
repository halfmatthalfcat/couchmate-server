package com.couchmate.util

import com.couchmate.common.dao.{ChannelDAO, RoomActivityAnalyticsDAO, UserActivityAnalyticsDAO}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.util.mail.AnalyticsReport
import com.couchmate.util.mail.Fragments.{banner, email, row}
import play.api.libs.json.Json
import redis.clients.jedis._
import scalatags.Text.all._

import java.time.format.{DateTimeFormatter, FormatStyle}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

object DbQueryTester {
  def main(args: Array[String]): Unit = {
    implicit val db: Database = Database.forConfig("db")
    implicit val redis: JedisPool = new JedisPool()
  }
}
