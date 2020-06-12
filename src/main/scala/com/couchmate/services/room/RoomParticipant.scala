package com.couchmate.services.room

import java.util.UUID

import akka.actor.typed.ActorRef
import com.couchmate.services.room.Chatroom.Command

case class RoomParticipant(
  userId: UUID,
  username: String,
  actorRef: ActorRef[Command]
)
