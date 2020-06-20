package com.couchmate.api.ws

import java.util.UUID

import com.couchmate.services.room.RoomId

case class RoomContext(
  airingId: UUID,
  roomId: RoomId
)
