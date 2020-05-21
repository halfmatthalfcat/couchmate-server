package com.couchmate.data.wire

/**
 * Any Message Passed Over-The-Wire (externally)
 * Includes over WebSocket, AMQP or Intra-Cluster
 */

import julienrf.json.derived
import play.api.libs.json._

sealed trait WireMessage

object WireMessage {
  implicit val format: Format[WireMessage] =
    derived.flat.oformat((__ \ "type").format[String])
}

case class SendMessage(message: String) extends WireMessage
case class AppendMessage(message: String) extends WireMessage