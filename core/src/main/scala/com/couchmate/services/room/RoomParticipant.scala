package com.couchmate.services.room

import java.util.UUID

import akka.actor.typed.ActorRef
import com.couchmate.common.models.api.room.Participant
import com.couchmate.services.room.Chatroom.Command

case class RoomParticipant(
  userId: UUID,
  username: String,
  actorRef: ActorRef[Command]
)

object RoomParticipant {
  implicit def toParticipant(self: RoomParticipant): Participant =
    Participant(self.userId, self.username)
}