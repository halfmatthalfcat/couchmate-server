package com.couchmate.services.room

import java.util.UUID

import akka.actor.typed.scaladsl.ActorContext
import com.couchmate.common.models.api.room.{MessageType, Participant, Reaction}
import com.couchmate.services.room.Chatroom.{Command, OutgoingRoomMessage, UpdateRoomMessage}
import com.couchmate.util.akka.extensions.UserExtension

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

  def addParticipant(participant: Participant): HashRoom = {
    if (rooms.head.isFull) {
      this.copy(rooms = rooms + Room(
        RoomId(name, rooms.size + 1),
        List(participant)
      ))
    } else {
      this.copy(rooms = rooms.tail + rooms.head.addParticipant(participant))
    }
  }

  def removeParticipant(
    roomId: RoomId,
    userId: UUID
  ): HashRoom = this.copy(
    rooms =
      rooms
      .filterNot(_.roomId == roomId) + rooms
        .find(_.roomId == roomId)
        .get
        .removeParticipant(userId)
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
    Some(message),
    Some(participant)
  )

  def addReaction(
    roomId: RoomId,
    messageId: String,
    userId: UUID,
    shortCode: String
  ): Option[RoomMessage] = for {
    room <- getRoom(roomId)
    message <- room.messages.find(_.messageId == messageId)
    index = room.messages.indexWhere(_.messageId == messageId)
    reactionIdx = message.reactions.indexWhere(_.shortCode == shortCode)
    reaction = message.reactions.find(_.shortCode == shortCode).getOrElse(Reaction(
      shortCode = shortCode,
      reactors = Seq.empty
    ))
  } yield {
    // If the message and reaction exists _and_ the user hasn't already added
    if (
      index >= 0 &&
      reactionIdx >= 0 &&
      !reaction.reactors.contains(userId)
    ) {
      message.copy(reactions = message.reactions.updated(reactionIdx, reaction.copy(
        reactors = reaction.reactors :+ userId
      )))
    // If this is a new reaction _and_ the user hasn't already added
    } else if (
      index >= 0 &&
      !reaction.reactors.contains(userId)
    ) {
      message.copy(reactions = message.reactions :+ reaction.copy(
        reactors = reaction.reactors :+ userId
      ))
    } else { message }
  }

  def removeReaction(
    roomId: RoomId,
    messageId: String,
    userId: UUID,
    shortCode: String
  ): Option[RoomMessage] = for {
    room <- getRoom(roomId)
    message <- room.messages.find(_.messageId == messageId)
    index = room.messages.indexWhere(_.messageId == messageId)
    reactionIdx = message.reactions.indexWhere(_.shortCode == shortCode)
    reaction = message.reactions.find(_.shortCode == shortCode).getOrElse(Reaction(
      shortCode = shortCode,
      reactors = Seq.empty
    ))
  } yield {
    // If the message and reaction exists _and_ the user hasn't already added
    if (
      index >= 0 &&
      reactionIdx >= 0 &&
      reaction.reactors.contains(userId)
    ) {
      message.copy(reactions = message.reactions.updated(reactionIdx, reaction.copy(
        reactors = reaction.reactors.filter(_ != userId)
      )))
    } else { message }
  }

  def addMessage(roomId: RoomId, message: RoomMessage): Option[HashRoom] =
    getRoom(roomId).map(room => this.copy(
      rooms = getAllButRoom(roomId) + room.addMessage(message)
    ))

  def updateMessage(roomId: RoomId, message: RoomMessage): Option[HashRoom] = for {
    room <- getRoom(roomId)
    index = room.messages.indexWhere(_.messageId == message.messageId)
  } yield {
    if (index >= 0) {
      this.copy(
        rooms = getAllButRoom(roomId) + room.copy(
          messages = room.messages.updated(index, message)
        )
      )
    } else { this }
  }

  def broadcastMessage(roomId: RoomId, message: RoomMessage)(
    implicit
    userExtension: UserExtension
  ): Unit = {
    getRoom(roomId).fold(()) { room =>
      room.participants.foreach(p => userExtension.roomMessage(
        p.userId,
        OutgoingRoomMessage(message)
      ))
    }
  }

  def broadcastUpdateMessage(roomId: RoomId, message: RoomMessage)(
    implicit
    userExtension: UserExtension
  ): Unit = {
    getRoom(roomId).fold(()) { room =>
      room.participants.foreach(p => userExtension.roomMessage(
        p.userId,
        UpdateRoomMessage(message)
      ))
    }
  }

  def broadcastAll(message: RoomMessage)(
    implicit
    userExtension: UserExtension
  ): Unit =
    rooms.foreach(_.participants.foreach(p => userExtension.roomMessage(
      p.userId,
      OutgoingRoomMessage(message)
    )))

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
