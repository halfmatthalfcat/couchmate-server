package com.couchmate.services.room

import java.util.UUID

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import com.couchmate.common.models.api.room.MessageType
import com.couchmate.services.room.Chatroom.{Command, OutgoingRoomMessage}

import scala.collection.immutable.SortedSet

case class HashRoom private (
  name: String,
  rooms: SortedSet[Room]
)(
  implicit
  ctx: ActorContext[Command]
) {

  def getRoom(roomId: RoomId): Option[Room] =
    rooms.find(_.roomId == roomId)

  def getAllButRoom(roomId: RoomId): SortedSet[Room] =
    rooms.filter(_.roomId != roomId)

  def addParticipant(
    userId: UUID,
    username: String,
    actorRef: ActorRef[Command]
  ): HashRoom = {
    if (rooms.head.isFull) {
      this.copy(rooms = rooms + Room(
        RoomId(name, rooms.size + 1),
        List(RoomParticipant(
          userId, username, actorRef
        ))
      ))
    } else {
      this.copy(rooms = rooms.tail + rooms.head.addParticipant(RoomParticipant(
        userId, username, actorRef
      )))
    }
  }

  def removeParticipant(
    roomId: RoomId,
    participant: RoomParticipant
  ): HashRoom = this.copy(
    rooms =
      rooms
      .filterNot(_.roomId == roomId) + rooms
        .find(_.roomId == roomId)
        .get
        .removeParticipant(participant.actorRef)
  )

  def getParticipantRoom(userId: UUID): Option[Room] =
    rooms.find(_.getParticipant(userId).nonEmpty)

  def createRoomMessage(
    roomId: RoomId,
    messageType: MessageType,
    userId: UUID,
    message: String
  ): Option[RoomMessage] = for {
    room <- getRoom(roomId)
    participant <- room.getParticipant(userId)
  } yield RoomMessage(
    messageType,
    message,
    Some(participant)
  )

  def addMessage(roomId: RoomId, message: RoomMessage): Option[HashRoom] =
    getRoom(roomId).map(room => this.copy(
      rooms = getAllButRoom(roomId) + room.addMessage(message)
    ))

  def broadcastMessage(roomId: RoomId, message: RoomMessage): Unit = {
    getRoom(roomId).fold(()) { room =>
      room.participants.map(_.actorRef).foreach(_ ! OutgoingRoomMessage(message))
    }
  }

  def broadcastAll(message: RoomMessage): Unit =
    rooms.foreach(_.participants.map(_.actorRef).foreach(_ ! OutgoingRoomMessage(message)))

  def getLastMessage(roomId: RoomId): Option[RoomMessage] =
    getRoom(roomId).flatMap(_.messages.headOption)
}

object HashRoom {
  def apply(name: String)(
    implicit
    ctx: ActorContext[Command]
  ): HashRoom =
    new HashRoom(name, SortedSet(
      Room(RoomId(
        name, 1
      ))
    ))
}
