package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.RoomActivityQueries
import com.couchmate.data.db.table.RoomActivityTable
import com.couchmate.data.models.RoomActivity

import scala.concurrent.{ExecutionContext, Future}

class RoomActivityDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends RoomActivityQueries {

  def getRoomCount(airingId: UUID): Future[Int] = {
    db.run(super.getRoomCount(airingId).result)
  }

  def addRoomActivity(roomActivity: RoomActivity): Future[RoomActivity] = {
    db.run((RoomActivityTable.table returning RoomActivityTable.table) += roomActivity)
  }

}
