package com.couchmate.db.dao

import java.util.UUID

import com.couchmate.common.models.RoomActivity
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.RoomActivityQueries
import com.couchmate.db.table.RoomActivityTable

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
