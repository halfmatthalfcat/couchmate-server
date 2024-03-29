package com.couchmate.services.room

import java.util.UUID

import akka.actor.typed.scaladsl.ActorContext
import com.couchmate.common.models.api.room.message.{Message, MessageReference, MessageReferenceType, Reactable, TenorMessage, TextMessage}
import com.couchmate.common.models.api.room.{Participant, Reaction}
import com.couchmate.services.room.Chatroom.{Command, OutgoingRoomMessage, UpdateRoomMessage}
import com.couchmate.services.user.PersistentUser
import com.couchmate.util.akka.extensions.UserExtension

import scala.collection.immutable.SortedSet

case class HashRoom private (
  name: String,
  rooms: SortedSet[Room]
) {

  def getRoom(roomId: RoomId): Option[Room] =
    rooms.find(_.roomId == roomId)

  def getAllButRoom(roomId: RoomId): SortedSet[Room] =
    rooms.filter(_.roomId != roomId)

  def getTotalParticipants: List[Participant] =
    rooms.foldLeft(List.empty[Participant])(_ ++ _.participants)

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

  def removeParticipant(userId: UUID): HashRoom = this.copy(
    rooms = rooms.map(_.removeParticipant(userId))
  )

  def getParticipantRoom(userId: UUID): Option[Room] =
    rooms.find(_.getParticipant(userId).nonEmpty)

  def createTextMessage(
    roomId: RoomId,
    userId: UUID,
    message: String,
    rooms: Map[String, Int]
  ): Option[TextMessage] = for {
    room <- getRoom(roomId)
    participant <- room.getParticipant(userId)
  } yield TextMessage(
    message,
    participant,
    List.empty,
    findReferences(
      message,
      room.participants,
      rooms
    ),
    isSelf = true
  )

  def createTenorMessage(
    roomId: RoomId,
    userId: UUID,
    url: String
  ): Option[TenorMessage] = for {
    room <- getRoom(roomId)
    participant <- room.getParticipant(userId)
  } yield TenorMessage(
    participant,
    List.empty,
    isSelf = true,
    url
  )

  def addReaction(
    roomId: RoomId,
    messageId: String,
    userId: UUID,
    shortCode: String
  ): Option[Message with Reactable] = for {
    room <- getRoom(roomId)
    message <- room.messages.collectFirst {
      case m: Message with Reactable if m.messageId == messageId => m
    }
  } yield message.addReaction(userId, shortCode)

  def removeReaction(
    roomId: RoomId,
    messageId: String,
    userId: UUID,
    shortCode: String
  ): Option[Message with Reactable] = for {
    room <- getRoom(roomId)
    message <- room.messages.collectFirst {
      case m: Message with Reactable if m.messageId == messageId => m
    }
  } yield message.removeReaction(userId, shortCode)

  def addMessage(roomId: RoomId, message: Message): Option[HashRoom] =
    getRoom(roomId).map(room => this.copy(
      rooms = getAllButRoom(roomId) + room.addMessage(message)
    ))

  def updateMessage(roomId: RoomId, message: Message): Option[HashRoom] = for {
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

  def broadcastMessage(roomId: RoomId, message: Message)(
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

  def broadcastUpdateMessage(roomId: RoomId, message: Message)(
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

  def broadcastAll(message: Message)(
    implicit
    userExtension: UserExtension
  ): Unit =
    rooms.foreach(_.participants.foreach(p => userExtension.roomMessage(
      p.userId,
      OutgoingRoomMessage(message)
    )))

  def broadcastInCluster(message: Command)(
    implicit
    userExtension: UserExtension
  ): Unit = getTotalParticipants.foreach(p => userExtension.roomMessage(
    p.userId,
    message
  ))

  def getLastMessage(roomId: RoomId): Option[Message] =
    getRoom(roomId).flatMap(_.messages.headOption)

  private[this] def findReferences(
    message: String,
    participants: List[Participant],
    rooms: Map[String, Int]
  ): List[MessageReference] = {
    val userReferences: List[MessageReference] = participants
      .map(p => List.from(s"@${p.username}"
        .r.findAllMatchIn(message)
        .map(m => MessageReference(
          messageReferenceType = MessageReferenceType.User,
          id = p.userId.toString,
          value = p.username,
          startIdx = m.start,
          endIdx = m.end
        ))))
      .foldLeft(List.empty[MessageReference])(_ ++ _)
    val hashReferences: List[MessageReference] =
      List.from("(?:\\s?#(\\w+)\\s?)".r.findAllMatchIn(message))
        .map(m => MessageReference(
          messageReferenceType = MessageReferenceType.HashRoom,
          id = m.group(1),
          value = rooms
            .getOrElse(m.group(1), 0)
            .toString,
          startIdx = m.start,
          endIdx = m.end
        ))
    userReferences ++ hashReferences
  }
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
