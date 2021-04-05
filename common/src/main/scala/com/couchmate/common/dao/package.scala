package com.couchmate.common

import com.couchmate.common.db.PgProfile.api._
import kamon.Kamon
import kamon.instrumentation.futures.scala.ScalaFutureInstrumentation.traceBody
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
    bust: Boolean = false,
    bustFn: String => Future[_] = _ => Future.successful()
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    caffeine: CaffeineCache[String],
    redis: RedisCache[String]
  ): Future[T] = {
    val fqCacheKey: String =
      DefaultCacheKeyBuilder().toCacheKey(cacheKey)
    val span = Kamon.currentSpan()
    span.tag("cacheable", value = true)
    span.tag("cache-key", fqCacheKey)
    traceBody(fqCacheKey)(for {
      // If we're busting cache, bust it
      _ <- if (bust) {
        span.tag("force-bust", value = true)
        (for {
          _ <- caffeine.remove(cacheKey: _*)
          _ <- redis.remove(cacheKey: _*)
          _ <- bustFn(fqCacheKey)
        } yield ()) recover {
          case _: Throwable => Future.successful()
        }
      } else {
        span.tag("force-bust", value = false)
        Future.successful()
      }
      // First look in-memory for the cache item
      inMemory <- traceBody("get-caffeine")(get(cacheKey: _*)(
        cache = caffeine,
        mode = implicitly[Mode[Future]],
        flags = Flags()
      ))
      // If there isn't anything in memory, continue
      inMemoryValue <- inMemory.fold({
        span.tag("in-caffeine", value = false)
        Future.successful(Option.empty[T])
        // If there is something in memory, decode it
      })(value => {
        span.tag("in-caffeine", value = true)
        Future.successful(Json.parse(value).asOpt[T])
      })
      // If it doesn't exist in memory, check Redis
      inRedis <- inMemoryValue.fold[Future[Option[T]]](for {
        // Get the item from Redis
        redisItem <- traceBody("get-redis")(get(cacheKey: _*)(
          cache = redis,
          mode = implicitly[Mode[Future]],
          flags = Flags()
        )) recover {
          case _: Throwable => Option.empty
        }
        // If the item doesn't exist in Redis, continue on
        redisItemValue <- redisItem.fold({
          span.tag("in-redis", value = false)
          Future.successful(Option.empty[T])
          // If there is something in redis, decode it
        })(value => {
          span.tag("in-redis", value = true)
          Future.successful(Json.parse(value).asOpt[T])
        })
        // No decoded value, continue
        inMemItem <- redisItemValue.fold(
          Future.successful(Option.empty[T])
          // If the item does exist in Redis, repopulate the
          // in-memory cache
        )(rItemValue => for {
          _ <- traceBody("put-caffeine")(put(cacheKey: _*)(
            Json.toJson(rItemValue).toString,
            ttl.map(_ / ratio.getOrElse(1))
          )(
            cache = caffeine,
            mode = implicitly[Mode[Future]],
            flags = Flags()
          ))
        } yield Some(rItemValue))
      } yield inMemItem)(item => Future.successful(Some(item)))
      value <- inRedis.fold(for {
        resolvedValue <- traceBody("get-value")(fn)
        _ <- traceBody("put-caffeine")(put(cacheKey: _*)(
          Json.toJson(resolvedValue).toString,
          ttl.map(_ / ratio.getOrElse(1))
        )(
          cache = caffeine,
          mode = implicitly[Mode[Future]],
          flags = Flags()
        ))
        _ <- traceBody("put-redis")(put(cacheKey: _*)(
          Json.toJson(resolvedValue).toString,
          ttl
        )(
          cache = redis,
          mode = implicitly[Mode[Future]],
          flags = Flags()
        )) recoverWith {
          case _: Throwable => Future.successful()
        }
      } yield resolvedValue)(Future.successful)
    } yield value)
  }
}
