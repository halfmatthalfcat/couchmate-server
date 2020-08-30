package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import java.util.UUID

import com.couchmate.common.models.data.{RoomActivity, RoomActivityType}
import com.couchmate.common.tables.RoomActivityTable

import scala.concurrent.Future

trait RoomActivityDAO {

  def getRoomCount(airingId: String)(
    implicit
    db: Database
  ): Future[Int] =
    db.run(RoomActivityDAO.getRoomCount(airingId))

  def getRoomCount$()(
    implicit
    session: SlickSession
  ): Flow[String, Int, NotUsed] =
    Slick.flowWithPassThrough(RoomActivityDAO.getRoomCount)

  def addRoomActivity(roomActivity: RoomActivity)(
    implicit
    db: Database
  ): Future[RoomActivity] =
    db.run(RoomActivityDAO.addRoomActivity(roomActivity))

  def addRoomActivity$()(
    implicit
    session: SlickSession
  ): Flow[RoomActivity, RoomActivity, NotUsed] =
    Slick.flowWithPassThrough(RoomActivityDAO.addRoomActivity)
}

object RoomActivityDAO {
  private[this] lazy val getUserLatestQuery =
    RoomActivityTable.table
      .groupBy(_.userId)
      .map {
        case (userId, query) =>
          userId -> query.map(_.created).max
      }

  private[this] lazy val getRoomCountQuery = Compiled { (airingId: Rep[String]) =>
    (for {
      counts <- getUserLatestQuery
      ra <- RoomActivityTable.table if (
        ra.airingId === airingId &&
        ra.userId === counts._1 &&
        ra.created === counts._2 &&
        ra.action === (RoomActivityType.Joined: RoomActivityType)
      )
    } yield ra).length
  }

  private[common] def getRoomCount(airingId: String): DBIO[Int] =
    getRoomCountQuery(airingId).result

  private[common] def addRoomActivity(roomActivity: RoomActivity): DBIO[RoomActivity] =
    (RoomActivityTable.table returning RoomActivityTable.table) += roomActivity
}
