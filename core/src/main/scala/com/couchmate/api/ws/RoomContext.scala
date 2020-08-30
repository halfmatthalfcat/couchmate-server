package com.couchmate.api.ws

import com.couchmate.services.room.RoomId

case class RoomContext(
  airingId: String,
  roomId: RoomId
)
