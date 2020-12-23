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
  private[common] lazy val getUserLatestQuery =
    RoomActivityTable.table
      .groupBy(_.userId)
      .map {
        case (userId, query) =>
          userId -> query.map(_.created).max
      }

  private[common] lazy val getRoomCountQuery =
    RoomActivityTable
      .table
      .filter(_.action === (RoomActivityType.Joined: RoomActivityType))
      .join(getUserLatestQuery)
      .on { case (current, (userId, latest)) =>
        current.userId === userId &&
        current.created === latest
      }.groupBy(_._1.airingId)

  private[common] def addRoomActivity(roomActivity: RoomActivity): DBIO[RoomActivity] =
    (RoomActivityTable.table returning RoomActivityTable.table) += roomActivity
}
