package com.couchmate.services.thirdparty.gracenote.listing

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.scalalogging.LazyLogging

object ListingCoordinator extends LazyLogging {
  sealed trait Command
  case class RequestListing(extId: String, actorRef: ActorRef[ListingJob.Command]) extends Command
  case class RemoveListing(extId: String) extends Command

  def apply(
    listingIngestor: ListingIngestor,
  ): Behavior[Command] = Behaviors.setup { ctx =>
    val jobMapper: ActorRef[ListingJob.Command] = ctx.messageAdapter[ListingJob.Command] {
      case ListingJob.JobEnded(extId) => RemoveListing(extId)
    }

    def run(jobs: Map[String, ActorRef[ListingJob.Command]]): Behavior[Command] = Behaviors.receiveMessage {
      case RequestListing(extId, actorRef) =>
        jobs.get(extId).fold {
          val job: ActorRef[ListingJob.Command] =
            ctx.spawn(ListingJob(extId, listingIngestor, actorRef, jobMapper), extId)

          run(jobs + (extId -> job))
        } { job =>
          job ! ListingJob.AddListener(actorRef)
          Behaviors.same
        }
      case RemoveListing(extId) =>
        run(jobs - extId)
    }

    run(Map())
  }
}
