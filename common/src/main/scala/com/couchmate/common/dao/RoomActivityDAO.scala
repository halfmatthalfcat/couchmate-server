package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.RoomActivity
import com.couchmate.common.tables.RoomActivityTable

import scala.concurrent.Future

object RoomActivityDAO {
  def addRoomActivity(roomActivity: RoomActivity)(
    implicit
    db: Database
  ): Future[RoomActivity] =
    db.run((RoomActivityTable.table returning RoomActivityTable.table) += roomActivity)
}
