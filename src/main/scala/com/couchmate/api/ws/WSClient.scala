package com.couchmate.api.ws

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.couchmate.Server
import com.couchmate.util.akka.AkkaUtils

import scala.concurrent.ExecutionContext

object WSClient extends AkkaUtils {
  def apply()(
    implicit
    ec: ExecutionContext,
    context: ActorContext[Server.Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage {
      case SocketConnected(actorRef) =>
        Behaviors.receiveMessage(compose(
          internal(ctx),
          new Connected(ctx, actorRef).run
        ))
      case _ => Behaviors.ignore
    }
  }

  def internal(ctx: ActorContext[Command]): PartialCommand = {
    case Closed | Complete =>
      ctx.log.debug("Connecting closing")
      Behaviors.stopped
    case ConnFailure(ex) =>
      ctx.log.error("Connection failed", ex)
      Behaviors.stopped
    case Failed(ex) =>
      ctx.log.error("WSActor failed", ex)
      Behaviors.stopped
  }
}
