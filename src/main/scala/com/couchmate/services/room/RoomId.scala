package com.couchmate.services.room

case class RoomId(name: String, replica: Int) {
  override def toString: String =
    s"$name|$replica"
}
