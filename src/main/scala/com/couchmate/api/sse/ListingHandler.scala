package com.couchmate.api.sse

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, BehaviorInterceptor, TypedActorContext}
import akka.http.scaladsl.model.sse.ServerSentEvent
import com.couchmate.services.thirdparty.gracenote.listing.{ListingCoordinator, ListingJob}

object ListingHandler {
  sealed trait Command

  case class Connected(actorRef: ActorRef[ServerSentEvent]) extends Command
  case object Ack extends Command

  case object Finished extends Command
  case class Progress(progress: Double) extends Command

  def apply(
    extId: String,
    actorRef: ActorRef[ListingCoordinator.Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    val jobAdapter = ctx.messageAdapter[ListingJob.Command] {
      case ListingJob.JobEnded(_) => Finished
      case ListingJob.JobProgress(progress) => Progress(progress)
    }

    actorRef ! ListingCoordinator.RequestListing(extId, jobAdapter)

    def run(socket: Option[ActorRef[ServerSentEvent]]): Behavior[Command] = Behaviors.receiveMessage {
      case Connected(actorRef) =>
        run(Some(actorRef))
      case Progress(progress) =>
        socket.fold(()) { resolvedSocket =>
          resolvedSocket ! ServerSentEvent(
            eventType = Some("progress"),
            data = progress.toString
          )
        }
        Behaviors.same
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
    extId: String,
    actorRef: ActorRef[ListingCoordinator.Command],
  ): Behavior[SSEHandler.Command] = SSEHandler.interceptor(apply(extId, actorRef)) {
    case SSEHandler.Connected(actorRef) => Connected(actorRef)
    // case SSEHandler.Complete => ()
  }
}
