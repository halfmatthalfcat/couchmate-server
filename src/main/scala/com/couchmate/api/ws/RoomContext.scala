package com.couchmate.api.ws

import java.util.UUID

import com.couchmate.services.room.RoomParticipant

case class RoomContext(
  roomId: UUID,
  participants: Seq[RoomParticipant]
)
