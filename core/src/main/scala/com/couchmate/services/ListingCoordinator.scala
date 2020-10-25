package com.couchmate.services

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.services.gracenote.listing.{ListingJob, ListingPullType}
import com.typesafe.scalalogging.LazyLogging

object ListingCoordinator extends LazyLogging {
  sealed trait Command
  case class RequestListing(
    providerId: Long,
    pullType: ListingPullType,
    actorRef: ActorRef[ListingJob.Command]
  ) extends Command
  case class RemoveListing(providerId: Long) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    val jobMapper: ActorRef[ListingJob.Command] = ctx.messageAdapter[ListingJob.Command] {
      case ListingJob.JobEnded(_, providerId, _) => RemoveListing(providerId)
    }

    def run(jobs: Map[Long, ActorRef[ListingJob.Command]]): Behavior[Command] = Behaviors.receiveMessage {
      case RequestListing(providerId, pullType, actorRef) =>
        jobs.get(providerId).fold {
          val job: ActorRef[ListingJob.Command] =
            ctx.spawn(ListingJob(
              UUID.randomUUID(),
              providerId,
              pullType,
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
