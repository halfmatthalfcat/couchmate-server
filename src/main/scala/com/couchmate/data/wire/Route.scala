package com.couchmate.data.wire

import java.util.UUID

sealed trait Route {
  def toRoute: String
}
object Route {
  implicit def toRoute(route: Route): String = route.toRoute
}

case class RoomRoute(roomId: UUID, subroom: Option[String] = None) extends Route {
  def toRoute: String = {
    if (subroom.nonEmpty) {
      s"room.${roomId.toString}.${subroom.get}"
    } else {
      s"room.${roomId.toString}"
    }
  }

}

case class UserRoute(userId: UUID) extends Route {
  def toRoute: String =
    s"user.${userId.toString}"
}
