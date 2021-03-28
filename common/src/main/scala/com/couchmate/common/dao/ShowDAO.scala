package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.Show
import com.couchmate.common.tables.ShowTable
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object ShowDAO {
  private[this] lazy val getShowQuery = Compiled { (showId: Rep[Long]) =>
    ShowTable.table.filter(_.showId === showId)
  }

  def getShow(showId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Show]] = cache(
    "getShow",
    showId
  )(db.run(getShowQuery(showId).result.headOption))()

  private[this] lazy val getShowByExtQuery = Compiled { (extId: Rep[Long]) =>
    ShowTable.table.filter(_.extId === extId)
  }

  def getShowByExt(extId: Long)(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[Show]] = cache(
    "getShowByExt",
    extId
  )(db.run(getShowByExtQuery(extId).result.headOption))(bust = bust)

  private[this] def addShowForId(s: Show)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addShowForId",
    s.extId
  )(db.run(
    sql"""SELECT insert_or_get_show_id(${s.extId}, ${s.`type`}, ${s.episodeId}, ${s.sportEventId}, ${s.title}, ${s.description}, ${s.originalAirDate})"""
      .as[Long].head
  ))()

  def addOrGetShow(s: Show)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Show] = cache(
    "addOrGetShow",
    s.extId
  )(for {
    exists <- getShowByExt(s.extId)()
    s <- exists.fold(for {
      _ <- addShowForId(s)
      selected <- getShowByExt(s.extId)(bust = true).map(_.get)
    } yield selected)(Future.successful)
  } yield s)()

}
