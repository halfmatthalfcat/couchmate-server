package com.couchmate.api

/**
 * WS Protocol
 */

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.api.ws.protocol.Protocol
import com.couchmate.services.room.{RoomId, RoomParticipant}

package object ws {
  object Commands {
    sealed trait Command
    type PartialCommand = PartialFunction[Command, Behavior[Command]]

    case class SocketConnected(actorRef: ActorRef[Command]) extends Command

    /**
     * Common Commands
     */

    case class Incoming(message: Protocol) extends Command
    case class Outgoing(message: Protocol) extends Command

    /**
     * Internal Commands
     */

    case object Closed                      extends Command
    case object Complete                    extends Command
    case class  ConnFailure(ex: Throwable)  extends Command
    case class  Failed(ex: Throwable)       extends Command

    object Connected {
      case class CreateNewSessionSuccess(
        session: SessionContext,
        geo: GeoContext
      ) extends Command
      case class CreateNewSessionFailure(err: Throwable) extends Command
    }

    object InRoom {
      case class RoomJoined(airingId: UUID, roomId: RoomId)           extends Command
      case class RoomRejoined(airingId: UUID, roomId: RoomId)         extends Command
      case class RoomEnded(airingId: UUID, roomId: RoomId)            extends Command
      case class SetParticipants(participants: Set[RoomParticipant])  extends Command
      case class AddParticipant(participant: RoomParticipant)         extends Command
      case class RemoveParticipant(participant: RoomParticipant)      extends Command
    }

    object Messaging {
      case class MessageSent(
        participant: RoomParticipant,
        message: String
      ) extends Command

      case object MessageQueued       extends Command
      case object MessageThrottled    extends Command
      case object MessageQueueClosed  extends Command
      case class  MessageQueueFailed(
        ex: Throwable
      ) extends Command
    }
  }
}
