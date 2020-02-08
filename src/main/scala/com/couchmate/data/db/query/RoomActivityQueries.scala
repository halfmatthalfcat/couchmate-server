package com.couchmate.data.db.query

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.RoomActivityTable
import com.couchmate.data.models.RoomActivityType

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
