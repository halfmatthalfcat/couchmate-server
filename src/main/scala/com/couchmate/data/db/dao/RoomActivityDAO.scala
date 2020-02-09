package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.RoomActivityTable
import com.couchmate.data.models.{RoomActivity, RoomActivityType}

import scala.concurrent.{ExecutionContext, Future}

class RoomActivityDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getRoomCount(airingId: UUID): Future[Int] = {
    db.run(RoomActivityDAO.getRoomCount(airingId).result)
  }

  def addRoomActivity(roomActivity: RoomActivity): Future[RoomActivity] = {
    db.run((RoomActivityTable.table returning RoomActivityTable.table) += roomActivity)
  }

}

object RoomActivityDAO {
  private[this] lazy val getUserLatest =
    RoomActivityTable.table
      .groupBy(_.userId)
      .map {
        case (userId, query) =>
          userId -> query.map(_.created).max
      }

  private[dao] lazy val getRoomCount = Compiled { (airingId: Rep[UUID]) =>
    (for {
      counts <- getUserLatest
      ra <- RoomActivityTable.table if (
        ra.airingId === airingId &&
        ra.userId === counts._1 &&
        ra.created === counts._2 &&
        ra.action === (RoomActivityType.Joined: RoomActivityType)
      )
    } yield ra).length
  }
}
