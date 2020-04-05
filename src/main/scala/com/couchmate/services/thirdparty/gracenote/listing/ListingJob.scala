package com.couchmate.services.thirdparty.gracenote.listing

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

object ListingJob extends LazyLogging {
  sealed trait Command
  case class JobEnded(providerId: Long) extends Command
  case class JobProgress(progress: Double) extends Command
  case class AddListener(actorRef: ActorRef[Command]) extends Command

  def apply(
    providerId: Long,
    listingIngestor: ListingIngestor,
    initiate: ActorRef[Command],
    parent: ActorRef[Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    logger.debug(s"Starting job $providerId")
    implicit val system: ActorSystem[Nothing] = ctx.system
    implicit val ec: ExecutionContext = ctx.executionContext

    Source
      .single(providerId)
      .via(listingIngestor.ingestListings(ListingPullType.Initial))
      .to(Sink.foreach { progress =>
        ctx.self ! JobProgress(progress)
      }).run()

    def run(listeners: Seq[ActorRef[Command]]): Behavior[Command] = Behaviors.receiveMessage {
      case AddListener(listener) =>
        run(listeners :+ listener)
      case p @ JobProgress(progress) if progress < 1 =>
        listeners.foreach(_ ! p)
        Behaviors.same
      case JobProgress(progress) if progress == 1 =>
        listeners.foreach(_ ! JobEnded(providerId))
        parent ! JobEnded(providerId)
        Behaviors.stopped
    }

    run(Seq(initiate))
  }
}
