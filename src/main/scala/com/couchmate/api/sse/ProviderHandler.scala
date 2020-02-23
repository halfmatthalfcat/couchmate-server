package com.couchmate.api.sse

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.sse.ServerSentEvent
import com.couchmate.services.thirdparty.gracenote.provider.{ProviderCoordinator, ProviderJob}
import com.typesafe.scalalogging.LazyLogging

object ProviderHandler extends LazyLogging {
  sealed trait Command

  case class Connected(actorRef: ActorRef[ServerSentEvent]) extends Command
  case object Ack extends Command

  case object Finished extends Command

  def apply(
    zipCode: String,
    country: Option[String],
    actorRef: ActorRef[ProviderCoordinator.Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    val jobAdapter = ctx.messageAdapter[ProviderJob.Command] {
      case p: ProviderJob.JobEnded => Finished
    }

    actorRef ! ProviderCoordinator.RequestProviders(zipCode, country, jobAdapter)

    def run(socket: Option[ActorRef[ServerSentEvent]]): Behavior[Command] = Behaviors.receiveMessage {
      case Connected(actorRef) =>
        run(Some(actorRef))
      case Finished =>
        socket.fold(()) { resolvedSocket =>
          resolvedSocket ! ServerSentEvent(
            eventType = Some("complete"),
            data = "complete"
          )
        }
        Behaviors.stopped
    }

    run(None)
  }

  def sse(
    zipCode: String,
    country: Option[String],
    actorRef: ActorRef[ProviderCoordinator.Command],
  ): Behavior[SSEHandler.Command] = SSEHandler.interceptor(apply(zipCode, country, actorRef)) {
    case SSEHandler.Connected(actorRef) => Connected(actorRef)
    // case SSEHandler.Complete => ()
  }

}
