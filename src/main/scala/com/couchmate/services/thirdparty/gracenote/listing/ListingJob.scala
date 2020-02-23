package com.couchmate.services.thirdparty.gracenote.listing

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

object ListingJob extends LazyLogging {
  sealed trait Command
  case class JobEnded(extId: String) extends Command
  case class JobProgress(progress: Double) extends Command
  case class AddListener(actorRef: ActorRef[Command]) extends Command

  def apply(
    extId: String,
    listingIngestor: ListingIngestor,
    initiate: ActorRef[Command],
    parent: ActorRef[Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    logger.debug(s"Starting job $extId")
    implicit val system: ActorSystem[Nothing] = ctx.system
    implicit val ec: ExecutionContext = ctx.executionContext

    Source
      .single(extId)
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
      case _ =>
        parent ! JobEnded(extId)
        listeners.foreach(_ ! JobEnded(extId))
        Behaviors.stopped
    }

    run(Seq(initiate))
  }
}
