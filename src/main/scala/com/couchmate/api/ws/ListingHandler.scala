package com.couchmate.api.ws

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.ws.Message
import com.couchmate.api.models.WSMessage
import com.couchmate.api.models.grid.Grid
import com.couchmate.services.thirdparty.gracenote.listing.{ListingCoordinator, ListingJob}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json

object ListingHandler extends LazyLogging {
  sealed trait Command

  case class Connected(actorRef: ActorRef[Message]) extends Command
  case object Ack extends Command
  case object Stop extends Command

  case class Finished(grid: Grid) extends Command
  case class Progress(progress: Double) extends Command

  def apply(
    providerId: Long,
    actorRef: ActorRef[ListingCoordinator.Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    val jobAdapter = ctx.messageAdapter[ListingJob.Command] {
      case ListingJob.JobEnded(_, grid) => Finished(grid)
      case ListingJob.JobProgress(progress) => Progress(progress)
    }

    actorRef ! ListingCoordinator.RequestListing(providerId, jobAdapter)

    def run(socket: Option[ActorRef[Message]]): Behavior[Command] = Behaviors.receiveMessage {
      case Connected(actorRef) =>
        run(Some(actorRef))
      case Progress(progress) =>
        socket.fold(()) { resolvedSocket =>
          resolvedSocket ! WSMessage(
            "progress",
            Some(progress)
          )
        }
        Behaviors.same
      case Finished(grid) =>
        socket.fold(()) { resolvedSocket =>
          resolvedSocket ! WSMessage(
            "complete",
            Some(grid),
          )
        }
        Behaviors.stopped
      case Stop =>
        Behaviors.stopped
    }

    run(None)
  }

  def ws(
    providerId: Long,
    actorRef: ActorRef[ListingCoordinator.Command],
  ): Behavior[WSHandler.Command] = WSHandler.interceptor(apply(providerId, actorRef)) {
    case WSHandler.Connected(actorRef) => Connected(actorRef)
  }
}
