package com.couchmate.common

import com.couchmate.common.db.PgProfile.api._
import play.api.libs.json.{Format, JsNull, JsResult, JsValue, Json, JsonConfiguration, OptionHandlers, Writes}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache
import scalacache.modes.scalaFuture._
import scalacache._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

package object dao {
  implicit val config: JsonConfiguration =
    JsonConfiguration(
      optionHandlers = OptionHandlers.WritesNull
    )

  implicit def OptionFormat[T: Format]: Format[Option[T]] = new Format[Option[T]] {
    override def reads(
      json: JsValue,
    ): JsResult[Option[T]] = json.validateOpt[T]

    override def writes(o: Option[T]): JsValue = o match {
      case Some(t) => implicitly[Writes[T]].writes(t)
      case None => JsNull
    }
  }

  def cache[T: Format](cacheKey: Any*)(fn: => Future[T])(
    ttl: Option[FiniteDuration] = Some(10.seconds),
    ratio: Option[Int] = Some(2),
    bust: Boolean = false
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    caffeine: CaffeineCache[String],
    redis: RedisCache[String]
  ): Future[T] = for {
    // If we're busting cache, bust it
    _ <- if (bust) {
      for {
        _ <- caffeine.remove(cacheKey: _*)
        _ <- redis.remove(cacheKey: _*)
      } yield ()
    } else {
      Future.successful()
    }
    // First look in-memory for the cache item
    inMemory <- get(cacheKey: _*)(
      cache = caffeine,
      mode = implicitly[Mode[Future]],
      flags = Flags()
    )
    // If there isn't anything in memory, continue
    inMemoryValue <- inMemory.fold(
      Future.successful(Option.empty[T])
    // If there is something in memory, decode it
    )(value => Future.successful(Json.parse(value).asOpt[T]))
    // If it doesn't exist in memory, check Redis
    inRedis <- inMemoryValue.fold[Future[Option[T]]](for {
      // Get the item from Redis
      redisItem <- get(cacheKey: _*)(
        cache = redis,
        mode = implicitly[Mode[Future]],
        flags = Flags()
      ) recover {
        case _: Throwable => Option.empty
      }
      // If the item doesn't exist in Redis, continue on
      redisItemValue <- redisItem.fold(
        Future.successful(Option.empty[T])
      // If there is something in redis, decode it
      )(value => Future.successful(Json.parse(value).asOpt[T]))
      // No decoded value, continue
      inMemItem <- redisItemValue.fold(
        Future.successful(Option.empty[T])
      // If the item does exist in Redis, repopulate the
      // in-memory cache
      )(rItemValue => for {
        _ <- put(cacheKey: _*)(
          Json.toJson(rItemValue).toString,
          ttl.map(_ / ratio.getOrElse(1))
        )(
          cache = caffeine,
          mode = implicitly[Mode[Future]],
          flags = Flags()
        )
      } yield Some(rItemValue))
    } yield inMemItem)(item => Future.successful(Some(item)))
    value <- inRedis.fold(for {
      resolvedValue <- fn
      _ <- put(cacheKey: _*)(
        Json.toJson(resolvedValue).toString,
        ttl.map(_ / ratio.getOrElse(1))
      )(
        cache = caffeine,
        mode = implicitly[Mode[Future]],
        flags = Flags()
      )
      _ <- put(cacheKey: _*)(
        Json.toJson(resolvedValue).toString,
        ttl
      )(
        cache = redis,
        mode = implicitly[Mode[Future]],
        flags = Flags()
      ) recoverWith {
        case _: Throwable => Future.successful()
      }
    } yield resolvedValue)(Future.successful)
  } yield value
}
