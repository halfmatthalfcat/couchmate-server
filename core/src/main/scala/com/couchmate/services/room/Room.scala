package com.couchmate.services.room

/**
 * A Room is a collection of users that partake in some
 * communication together, usually underneath a "named" room,
 * such as "general"
 */

import java.util.UUID

import akka.actor.typed.ActorRef
import com.couchmate.common.models.api.room.Participant
import com.couchmate.services.room.Chatroom.Command
import com.typesafe.config.ConfigFactory

final case class Room(
  roomId: RoomId,
  participants: List[Participant] = List.empty,
  messages: List[RoomMessage] = List.empty
) {
  private[this] val config = ConfigFactory.load()
  private[this] val maxSize: Int =
    config.getInt("features.room.participant-size")
  private[this] val cacheSize: Int =
    config.getInt("features.room.message-cache-size")

  def addParticipant(participant: Participant): Room = this.copy(
    participants = participant :: participants
  )

  def removeParticipant(userId: UUID): Room = this.copy(
    participants = participants.filterNot(_.userId == userId)
  )

  def hasParticipant(userId: UUID): Boolean =
    participants.exists(_.userId == userId)

  def getParticipant(userId: UUID): Option[Participant] =
    participants.find(_.userId == userId)

  def size: Int = participants.size

  def isFull: Boolean = participants.size >= maxSize

  def addMessage(message: RoomMessage): Room = {
    if (messages.length == cacheSize) {
      this.copy(
        messages = message :: messages.dropRight(1)
      )
    } else {
      this.copy(
        messages = message :: messages
      )
    }
  }
}

object Room {
  implicit val ordering: Ordering[Room] =
    Ordering.by((_: Room).size)
}
