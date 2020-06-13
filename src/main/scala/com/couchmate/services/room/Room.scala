package com.couchmate.services.room

import java.util.UUID

import akka.actor.typed.ActorRef
import com.couchmate.services.room.Chatroom.Command
import com.typesafe.config.ConfigFactory

import scala.collection.immutable.Queue

final case class Room(
  roomId: UUID = UUID.randomUUID(),
  participants: Queue[RoomParticipant] = Queue.empty
) {
  private[this] val maxSize: Int =
    ConfigFactory.load().getInt("features.room.default-size")

  def add(participant: RoomParticipant): Room = this.copy(
    // We want a LIFO queue
    participants = participants.enqueue(participant).reverse
  )

  def remove(userId: UUID): Room = this.copy(
    participants = participants.filterNot(_.userId == userId)
  )
  def remove(actorRef: ActorRef[Command]): Room = this.copy(
    participants = participants.filterNot(_.actorRef == actorRef)
  )

  def broadcast(message: Command): Unit =
    participants.foreach(_.actorRef ! message)

  def hasParticipant(userId: UUID): Boolean =
    participants.exists(_.userId == userId)
  def hasParticipant(actorRef: ActorRef[Command]): Boolean =
    participants.exists(_.actorRef == actorRef)

  def size: Int = participants.size

  def isFull: Boolean = participants.size >= maxSize
}

object Room {
  implicit val ordering: Ordering[Room] =
    Ordering.by((_: Room).size).reverse
}
