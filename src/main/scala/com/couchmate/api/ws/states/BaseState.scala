package com.couchmate.api.ws.states

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.couchmate.api.ws.Commands.{Closed, Command, Complete, ConnFailure, Failed, Outgoing, PartialCommand}
import com.couchmate.util.akka.AkkaUtils

abstract class BaseState(
  ctx: ActorContext[Command],
  ws: ActorRef[Command]
) extends AkkaUtils {

  final def run: Behavior[Command] = Behaviors.logMessages(
    Behaviors.receiveMessage(compose(
      internal,
      incoming,
      outgoing,
      closing,
    ))
  )

  private[this] final def outgoing: PartialCommand = {
    case outgoing: Outgoing =>
      ws ! outgoing
      Behaviors.same
  }

  private[this] final def closing: PartialCommand = {
    case Complete =>
      ctx.log.debug("Connection complete")
      onClose()
      Behaviors.stopped
    case Closed =>
      ctx.log.debug("Connection closed")
      onClose()
      Behaviors.stopped
    case ConnFailure(ex) =>
      ctx.log.error("Connection failed", ex)
      onClose()
      Behaviors.stopped
    case Failed(ex) =>
      ctx.log.error("WSActor failed", ex)
      onClose()
      Behaviors.stopped
  }

  protected def internal: PartialCommand =
    PartialFunction.empty

  protected def incoming: PartialCommand =
    PartialFunction.empty

  protected def onClose(): Unit = ()
}
