package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.Show
import com.couchmate.common.tables.ShowTable

import scala.concurrent.{ExecutionContext, Future}

trait ShowDAO {

  def getShow(showId: Long)(
    implicit
    db: Database
  ): Future[Option[Show]] =
    db.run(ShowDAO.getShow(showId))

  def getShow$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Show], NotUsed] =
    Slick.flowWithPassThrough(ShowDAO.getShow)

  def getShowByExt(extId: Long)(
    implicit
    db: Database
  ): Future[Option[Show]] =
    db.run(ShowDAO.getShowByExt(extId))

  def getShowByExt$()(
    implicit
    session: SlickSession
  ): Flow[Long, Option[Show], NotUsed] =
    Slick.flowWithPassThrough(ShowDAO.getShowByExt)

  def upsertShow(show: Show)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Show] =
    db.run(ShowDAO.upsertShow(show))

  def upsertShow$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[Show, Show, NotUsed] =
    Slick.flowWithPassThrough(ShowDAO.upsertShow)

  def addOrGetShow(show: Show)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Show] =
    db.run(ShowDAO.addAndGetShow(show))
}

object ShowDAO {
  private[this] lazy val getShowQuery = Compiled { (showId: Rep[Long]) =>
    ShowTable.table.filter(_.showId === showId)
  }

  private[common] def getShow(showId: Long): DBIO[Option[Show]] =
    getShowQuery(showId).result.headOption

  private[this] lazy val getShowByExtQuery = Compiled { (extId: Rep[Long]) =>
    ShowTable.table.filter(_.extId === extId)
  }

  private[common] def getShowByExt(extId: Long): DBIO[Option[Show]] =
    getShowByExtQuery(extId).result.headOption

  private[common] def getShowByShow(show: Show): DBIO[Option[Show]] =
    show match {
      case Show(Some(showId), _, _, _, _, _, _, _) =>
        getShow(showId)
      case Show(None, extShowId, _, _, _, _, _, _) =>
        getShowByExt(extShowId)
      case _ => DBIO.successful(Option.empty)
    }

  private[common] def upsertShow(show: Show)(
    implicit
    ec: ExecutionContext
  ): DBIO[Show] =
    show.showId.fold[DBIO[Show]](
      (ShowTable.table returning ShowTable.table) += show
    ) { (showId: Long) => for {
      _ <- ShowTable
        .table
        .filter(_.showId === showId)
        .update(show)
      updated <- ShowDAO.getShow(showId)
    } yield updated.get}

  private[this] def addShowForId(s: Show) =
    sql"""SELECT insert_or_get_show_id(${s.extId}, ${s.`type`}, ${s.episodeId}, ${s.sportEventId}, ${s.title}, ${s.description}, ${s.originalAirDate})""".as[Long]

  private[common] def addAndGetShow(s: Show)(
    implicit
    ec: ExecutionContext
  ): DBIO[Show] = (for {
    showId <- addShowForId(s).head
    show <- getShowQuery(showId).result.head
  } yield show)

  private[common] def addOrGetShow(show: Show) =
    sql"""
         WITH input_rows(ext_id, type, episode_id, sport_event_id, title, description, original_air_date) AS (
          VALUES (${show.extId}, ${show.`type`}, ${show.episodeId}, ${show.sportEventId}, ${show.title}, ${show.description}, ${show.originalAirDate}::timestamp)
         ), ins AS (
          INSERT INTO show (ext_id, type, episode_id, sport_event_id, title, description, original_air_date)
          SELECT * FROM input_rows
          ON CONFLICT (ext_id) DO NOTHING
          RETURNING show_id, ext_id, type, episode_id, sport_event_id, title, description, original_air_date
         ), sel AS (
          SELECT show_id, ext_id, type, episode_id, sport_event_id, title, description, original_air_date
          FROM ins
          UNION ALL
          SELECT s.show_id, ext_id, s.type, s.episode_id, s.sport_event_id, s.title, s.description, s.original_air_date
          FROM input_rows
          JOIN show as s USING (ext_id)
         ), ups AS (
           INSERT INTO show AS shw (ext_id, type, episode_id, sport_event_id, title, description, original_air_date)
           SELECT i.*
           FROM   input_rows i
           LEFT   JOIN sel   s USING (ext_id)
           WHERE  s.ext_id IS NULL
           ON     CONFLICT (ext_id) DO UPDATE
           SET    type = excluded.type,
                  episode_id = excluded.episode_id,
                  sport_event_id = excluded.sport_event_id
           RETURNING show_id, ext_id, type, episode_id, sport_event_id, title, description, original_air_date
         )  SELECT show_id, ext_id, type, episode_id, sport_event_id, title, description, original_air_date FROM sel
            UNION  ALL
            TABLE  ups;
         """.as[Show]

}
