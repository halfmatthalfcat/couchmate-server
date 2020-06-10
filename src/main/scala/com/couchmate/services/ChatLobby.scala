package com.couchmate.services

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import com.couchmate.data.models.User

object ChatLobby {
  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("ChatLobby")

  sealed trait Command

  final case class JoinRoom(user: User, actorRef: ActorRef[Command]) extends Command

  def apply(roomId: UUID): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.debug(s"Starting room ${roomId.toString}")
    Behaviors.empty
  }
}
