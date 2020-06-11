package com.couchmate.api

/**
 * WS Protocol
 */

import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.api.ws.protocol.Protocol

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
      case object RoomJoined extends Command
    }
  }
}
