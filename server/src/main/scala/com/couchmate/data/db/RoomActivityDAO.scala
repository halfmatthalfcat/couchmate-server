package com.couchmate.data.db

import java.util.UUID

import com.couchmate.common.models._

class RoomActivityDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  private[this] val getUserLatest = quote {
    query[RoomActivity]
      .groupBy(_.userId)
      .map {
        case (userId, roomActivity) =>
          userId -> roomActivity.map(_.created).max
      }
  }

  def getRoomCount(airingId: UUID) = quote {
    (for {
      counts <- getUserLatest
      ra <- query[RoomActivity] if (
        ra.airingId == airingId &&
        ra.userId == counts._1 &&
        counts._2.contains(ra.created) &&
        // I guess we need to lift enumeratum enum values to get things to compiled
        // @see https://gitter.im/getquill/quill?at=5d9c84b6b385bf2cc695a160
        (ra.action == lift(RoomActivityType.Joined: RoomActivityType))
      )
    } yield ra).size
  }

  def addRoomActivity(roomActivity: RoomActivity) = quote {
    query[RoomActivity]
      .insert(lift(roomActivity))
      .returning(ra => ra)
  }
}
