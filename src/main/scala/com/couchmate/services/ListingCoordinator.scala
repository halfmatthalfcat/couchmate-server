package com.couchmate.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.external.gracenote.listing.{ListingJob, ListingPullType}
import com.typesafe.scalalogging.LazyLogging

object ListingCoordinator extends LazyLogging {
  sealed trait Command
  case class RequestListing(providerId: Long, actorRef: ActorRef[ListingJob.Command]) extends Command
  case class RemoveListing(providerId: Long) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    val jobMapper: ActorRef[ListingJob.Command] = ctx.messageAdapter[ListingJob.Command] {
      case ListingJob.JobEnded(providerId, _) => RemoveListing(providerId)
    }

    def run(jobs: Map[Long, ActorRef[ListingJob.Command]]): Behavior[Command] = Behaviors.receiveMessage {
      case RequestListing(providerId, actorRef) =>
        jobs.get(providerId).fold {
          val job: ActorRef[ListingJob.Command] =
            ctx.spawn(ListingJob(
              providerId,
              ListingPullType.Initial,
              actorRef,
              jobMapper
            ), providerId.toString)

          run(jobs + (providerId -> job))
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
