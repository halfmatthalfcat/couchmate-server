package com.couchmate.services.room

/**
 * A Room is a collection of users that partake in some
 * communication together, usually underneath a "named" room,
 * such as "general"
 */

import java.util.UUID

import akka.actor.typed.ActorRef
import com.couchmate.services.room.Chatroom.Command
import com.typesafe.config.ConfigFactory

final case class Room(
  roomId: RoomId,
  participants: List[RoomParticipant] = List.empty
) {
  private[this] val maxSize: Int =
    ConfigFactory.load().getInt("features.room.default-size")

  def add(participant: RoomParticipant): Room = this.copy(
    participants = participant :: participants
  )

  def remove(userId: UUID): Room = this.copy(
    participants = participants.filterNot(_.userId == userId)
  )
  def remove(actorRef: ActorRef[Command]): Room = this.copy(
    participants = participants.filterNot(_.actorRef == actorRef)
  )

  def hasParticipant(userId: UUID): Boolean =
    participants.exists(_.userId == userId)
  def hasParticipant(actorRef: ActorRef[Command]): Boolean =
    participants.exists(_.actorRef == actorRef)

  def getParticipant(userId: UUID): Option[RoomParticipant] =
    participants.find(_.userId == userId)
  def getParticipant(actorRef: ActorRef[Command]): Option[RoomParticipant] =
    participants.find(_.actorRef == actorRef)

  def size: Int = participants.size

  def isFull: Boolean = participants.size >= maxSize
}

object Room {
  implicit val ordering: Ordering[Room] =
    Ordering.by((_: Room).size).reverse
}
