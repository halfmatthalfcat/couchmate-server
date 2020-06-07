package com.couchmate.api.ws

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.couchmate.Server
import com.couchmate.api.ws.states.{Connected, InSession}
import com.couchmate.util.akka.AkkaUtils

import scala.concurrent.ExecutionContext

object WSClient extends AkkaUtils {
  import Commands._
  def apply()(
    implicit
    ec: ExecutionContext,
    context: ActorContext[Server.Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessagePartial {
      case SocketConnected(actorRef) =>
        new Connected(ctx, actorRef).run
    }
  }
}
