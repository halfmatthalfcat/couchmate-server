package com.couchmate.api.ws

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.couchmate.data.wire.{AppendMessage, IncomingWSMessage, OutgoingWSMessage}
import com.couchmate.util.akka.AkkaUtils

/**
 * A Connected WS Client
 */

class Connected private[ws] (
  ctx: ActorContext[Command],
  ws: ActorRef[OutgoingWSMessage]
) extends AkkaUtils {
  ws ! OutgoingWSMessage(AppendMessage("Get good"))

  def run(): PartialFunction[Command, Behavior[Command]] = {
    case Incoming(IncomingWSMessage(message)) =>
      ctx.log.debug(s"$message")
      Behaviors.same
  }
}
