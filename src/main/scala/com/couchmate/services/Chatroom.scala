package com.couchmate.services

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import com.couchmate.data.models.User
import com.typesafe.config.{Config, ConfigFactory}

object Chatroom {
  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Chatroom")

  sealed trait Command

  final case class JoinRoom(user: User, actorRef: ActorRef[Command]) extends Command
  final case object RoomJoined extends Command

  final case class Room(
    roomId: UUID = UUID.randomUUID(),
    users: Set[ActorRef[Command]] = Set()
  ) {
    private[this] val maxSize: Int =
      ConfigFactory.load().getInt("features.room.default-size")

    def add(actorRef: ActorRef[Command]): Room = this.copy(
      users = users + actorRef
    )

    def remove(actorRef: ActorRef[Command]): Room = this.copy(
      users = users.filterNot(_ == actorRef)
    )

    def size: Int = users.size

    def isFull: Boolean = users.size >= maxSize
  }

  def apply(airingId: UUID): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.debug(s"Starting room for airing ${airingId.toString}")

    Behaviors.empty
  }
}
