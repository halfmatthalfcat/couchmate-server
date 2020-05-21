package com.couchmate.data.wire

/**
 * Encompasses Messages Leaving The Source (usually an actor)
 */

sealed trait OutgoingWireMessage {
  val message: WireMessage
}

case class OutgoingAmqpMessage(
  message: WireMessage,
  route: Route
) extends OutgoingWireMessage

case class OutgoingWSMessage(
  message: WireMessage
) extends OutgoingWireMessage

case class OutgoingClusterMessage(
  message: WireMessage
) extends OutgoingWireMessage