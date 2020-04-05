package com.couchmate.api.sse

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.sse.ServerSentEvent
import com.couchmate.api.models.Provider
import com.couchmate.services.thirdparty.gracenote.provider.{ProviderCoordinator, ProviderJob}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json

object ProviderHandler extends LazyLogging {
  sealed trait Command

  case class Connected(actorRef: ActorRef[ServerSentEvent]) extends Command
  case object Ack extends Command

  case class AddProvider(provider: Provider) extends Command
  case object Finished extends Command

  def apply(
    zipCode: String,
    country: Option[String],
    actorRef: ActorRef[ProviderCoordinator.Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    val jobAdapter: ActorRef[ProviderJob.Command] = ctx.messageAdapter[ProviderJob.Command] {
      case p: ProviderJob.JobEnded => Finished
      case ProviderJob.AddProvider(provider) => AddProvider(provider)
    }

    actorRef ! ProviderCoordinator.RequestProviders(zipCode, country, jobAdapter)

    def run(socket: Option[ActorRef[ServerSentEvent]]): Behavior[Command] = Behaviors.receiveMessage {
      case Connected(actorRef) =>
        run(Some(actorRef))
      case AddProvider(provider) =>
        socket.fold(()) { resolvedSocket =>
          resolvedSocket ! ServerSentEvent(
            eventType = Some("addProvider"),
            data = Json.toJson(provider).toString()
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
    zipCode: String,
    country: Option[String],
    actorRef: ActorRef[ProviderCoordinator.Command],
  ): Behavior[SSEHandler.Command] = SSEHandler.interceptor(apply(zipCode, country, actorRef)) {
    case SSEHandler.Connected(actorRef) => Connected(actorRef)
    // case SSEHandler.Complete => ()
  }

}
