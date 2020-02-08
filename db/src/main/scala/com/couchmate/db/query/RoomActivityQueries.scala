package com.couchmate.db.query

import java.util.UUID

import com.couchmate.common.models.RoomActivityType
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.RoomActivityTable

trait RoomActivityQueries {

  private[this] lazy val getUserLatest =
    RoomActivityTable.table
      .groupBy(_.userId)
      .map {
        case (userId, query) =>
          userId -> query.map(_.created).max
      }

  private[db] lazy val getRoomCount = Compiled { (airingId: Rep[UUID]) =>
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
