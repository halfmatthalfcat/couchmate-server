package com.couchmate.api

/**
 * WS Protocol
 */

import akka.actor.typed.ActorRef
import com.couchmate.data.wire.{IncomingWireMessage, OutgoingWSMessage, OutgoingWireMessage}

package object ws {
  sealed trait Command
  case class SocketConnected(actorRef: ActorRef[OutgoingWSMessage]) extends Command

  /**
   * Common Commands
   */

  sealed trait Common extends Command

  case class Incoming(message: IncomingWireMessage) extends Common
  case class Outgoing(message: OutgoingWireMessage) extends Common

  /**
   * Internal Commands
   */
  sealed trait Internal extends Command

  case object Closed                      extends Internal
  case object Complete                    extends Internal
  case class  ConnFailure(ex: Throwable)  extends Internal
  case class  Failed(ex: Throwable)       extends Internal

  /**
   * Initialized Commands
   */

  sealed trait Initialized extends Common
}
