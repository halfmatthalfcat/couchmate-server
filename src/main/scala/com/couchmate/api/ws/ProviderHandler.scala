//package com.couchmate.api.ws
//
//import akka.actor.typed.scaladsl.Behaviors
//import akka.actor.typed.{ActorRef, Behavior}
//import akka.http.scaladsl.model.sse.ServerSentEvent
//import akka.http.scaladsl.model.ws.Message
//import com.couchmate.api.models.{Provider, WSMessage}
//import com.couchmate.external.gracenote.provider.{ProviderCoordinator, ProviderJob}
//import com.couchmate.util.stream.WSStream
//import com.typesafe.scalalogging.LazyLogging
//import play.api.libs.json.Json
//
//object ProviderHandler extends LazyLogging {
//  sealed trait Command
//
//  case class Connected(actorRef: ActorRef[Message]) extends Command
//  case object Ack extends Command
//  case object Stop extends Command
//
//  case class AddProvider(provider: Provider) extends Command
//  case object Finished extends Command
//
//  def apply(
//    zipCode: String,
//    country: Option[String],
//    actorRef: ActorRef[ProviderCoordinator.Command],
//  ): Behavior[Command] = Behaviors.setup { ctx =>
//    val jobAdapter: ActorRef[ProviderJob.Command] = ctx.messageAdapter[ProviderJob.Command] {
//      case _: ProviderJob.JobEnded => Finished
//      case ProviderJob.AddProvider(provider) => AddProvider(provider)
//    }
//
//    actorRef ! ProviderCoordinator.RequestProviders(zipCode, country, jobAdapter)
//
//    def run(socket: Option[ActorRef[Message]]): Behavior[Command] = Behaviors.receiveMessage {
//      case Connected(actorRef) =>
//        run(Some(actorRef))
//      case AddProvider(provider) =>
//        socket.fold(()) { resolvedSocket =>
//          resolvedSocket ! WSMessage(
//            "addProvider",
//            Some(provider)
//          )
//        }
//        Behaviors.same
//      case Finished =>
//        socket.fold(()) { resolvedSocket =>
//          resolvedSocket ! WSMessage(
//            "complete",
//            Some("complete")
//          )
//        }
//        Behaviors.stopped
//      case Stop =>
//        Behaviors.stopped
//    }
//
//    run(None)
//  }
//
//  def ws(
//    zipCode: String,
//    country: Option[String],
//    actorRef: ActorRef[ProviderCoordinator.Command],
//  ): Behavior[WSStream.Command] = WSStream.interceptor(apply(
//    zipCode,
//    country,
//    actorRef,
//  )) {
//    case WSStream.Connected(actorRef) => Connected(actorRef)
//    case WSStream.Complete => Stop
//  }
//
//}
