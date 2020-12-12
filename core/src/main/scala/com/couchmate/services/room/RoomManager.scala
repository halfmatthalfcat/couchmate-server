package com.couchmate.services.room

import java.util.UUID

import akka.actor.typed.scaladsl.ActorContext
import com.couchmate.common.models.api.room.message.Message
import com.couchmate.util.akka.extensions.UserExtension
import com.couchmate.common.models.api.room.Participant
import com.couchmate.services.room.Chatroom.RoomEnded

case class RoomManager(
  airingId: String,
  private val defaultRoom: String,
  private val rooms: Map[String, HashRoom]
) {

  /**
   * State Ops
   */

  def getDefaultHashRoom: HashRoom = rooms(defaultRoom)

  def getHashRoom(room: String): Option[HashRoom] =
    rooms.get(room)

  def getHashRoomOrDefault(room: String): HashRoom =
    getHashRoom(room).getOrElse(rooms(defaultRoom))

  def updateHashRoom(hashRoom: HashRoom): RoomManager =
    this.copy(
      rooms = rooms.updated(hashRoom.name, hashRoom)
    )

  def createHashRoom(room: String)(
    implicit
    ctx: ActorContext[Chatroom.Command]
  ): RoomManager =
    getHashRoom(room).fold(
      this.copy(
        rooms = this.rooms + (
          room -> HashRoom(room)
        )
      )
    )(_ => this)

  def getRoom(roomId: RoomId): Option[Room] =
    getHashRoom(roomId.name).flatMap(_.getRoom(roomId))

  def addParticipant(participant: Participant): RoomManager = this.copy(
    rooms = rooms.updated(defaultRoom, getDefaultHashRoom.addParticipant(participant))
  )

  def addParticipant(hash: String, participant: Participant)(
    implicit
    ctx: ActorContext[Chatroom.Command]
  ): RoomManager =
    getHashRoom(hash).fold {
      val room = createHashRoom(hash).getHashRoomOrDefault(hash).addParticipant(participant)
      this.copy(
        rooms = rooms.updated(room.name, room)
      )
    } { room =>
      this.copy(
        rooms = rooms.updated(room.name, room.addParticipant(participant))
      )
    }

  def removeParticipant(userId: UUID): RoomManager =
    userInRoom(userId).fold(this)(hashRoom => this.copy(
      rooms = rooms.updated(hashRoom.name, hashRoom.removeParticipant(
        userId
      ))
    ))

  def removeParticipant(participant: Participant): RoomManager =
    removeParticipant(participant.userId)

  def userInRoom(participant: Participant): Option[HashRoom] =
    rooms.values.find(_.getParticipantRoom(participant.userId).nonEmpty)

  def userInRoom(userId: UUID): Option[HashRoom] =
    rooms.values.find(_.getParticipantRoom(userId).nonEmpty)

  def join(hash: Option[String], participant: Participant)(
    implicit
    ctx: ActorContext[Chatroom.Command]
  ): RoomManager =
    removeParticipant(participant)
      .createHashRoom(hash.getOrElse(defaultRoom))
      .addParticipant(hash.getOrElse(defaultRoom), participant)

  def addMessage(
    roomId: RoomId,
    message: Message
  ): RoomManager = (for {
    room <- getHashRoom(roomId.name)
    updatedRoom <- room.addMessage(roomId, message)
  } yield updateHashRoom(updatedRoom)).getOrElse(this)

  def updateMessage(
    roomId: RoomId,
    message: Message
  ): RoomManager = (for {
    room <- getHashRoom(roomId.name)
    updatedRoom <- room.updateMessage(roomId, message)
  } yield updateHashRoom(updatedRoom)).getOrElse(this)

  def getHashRoomCounts: Map[String, Int] = rooms.map {
    case (room, hRoom) => room -> hRoom.getTotalParticipants.size
  }

  /**
   * Side Effects
   */

  def messageRoom(room: RoomId, message: Message)(
    implicit
    userExtension: UserExtension
  ): Unit =
    getHashRoom(room.name).fold(())(_.broadcastAll(message))

  def messageRoom(room: String, message: Message)(
    implicit
    userExtension: UserExtension
  ): Unit =
    getHashRoom(room).fold(())(_.broadcastAll(message))

  def messageAll(message: Message)(
    implicit
    userExtension: UserExtension
  ): Unit = rooms.foreachEntry {
    case (_, hashRoom) => hashRoom.broadcastAll(message)
  }

  def kickAll()(
    implicit
    userExtension: UserExtension
  ): Unit = rooms.foreachEntry {
    case (_, hashRoom) => hashRoom.broadcastInCluster(RoomEnded(
      airingId
    ))
  }
}

object RoomManager {
  def apply(
    airingId: String,
    defaultRoom: String
  )(
    implicit
    ctx: ActorContext[Chatroom.Command],
    user: UserExtension
  ): RoomManager =
    new RoomManager(
      airingId,
      defaultRoom,
      Map(
        defaultRoom -> HashRoom(defaultRoom)
      )
    )
}
