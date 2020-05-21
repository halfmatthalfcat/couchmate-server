package com.couchmate.data.wire

/**
 * Encompasses Messages Coming Into The Source (usually an actor)
 */

sealed trait IncomingWireMessage {
  val message: WireMessage
}

case class IncomingWSMessage(
  message: WireMessage
) extends IncomingWireMessage

case class IncomingAmqpMessage(
  message: WireMessage
) extends IncomingWireMessage

case class IncomingClusterMessage(
  message: WireMessage
) extends IncomingWireMessage