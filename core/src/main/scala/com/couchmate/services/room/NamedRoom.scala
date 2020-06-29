package com.couchmate.services.room

import java.util.UUID

import akka.actor.typed.ActorRef
import com.couchmate.services.room.Chatroom.Command

import scala.collection.immutable.SortedSet

case class NamedRoom private (
  name: String,
  rooms: SortedSet[Room]
) {
  def addParticipant(
    userId: UUID,
    username: String,
    actorRef: ActorRef[Command]
  ): NamedRoom = {
    if (rooms.head.isFull) {
      this.copy(rooms = rooms + Room(
        RoomId(name, rooms.size + 1),
        List(RoomParticipant(
          userId, username, actorRef
        ))
      ))
    } else {
      this.copy(rooms = rooms.tail + rooms.head.add(RoomParticipant(
        userId, username, actorRef
      )))
    }
  }

  def removeParticipant(
    roomId: RoomId,
    participant: RoomParticipant
  ): NamedRoom = this.copy(
    rooms =
      rooms
      .filterNot(_.roomId == roomId) + rooms
        .find(_.roomId == roomId)
        .get
        .remove(participant.actorRef)
  )

  def getParticipantRoom(userId: UUID): Option[Room] =
    rooms.find(_.getParticipant(userId).nonEmpty)
}

object NamedRoom {
  def apply(name: String): NamedRoom =
    new NamedRoom(name, SortedSet(
      Room(RoomId(
        name, 1
      ))
    ))
}
