package com.couchmate.services.thirdparty.gracenote.provider

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.scalalogging.LazyLogging

object ProviderCoordinator extends LazyLogging {
  sealed trait Command
  case class RequestProviders(zipCode: String, country: Option[String], actorRef: ActorRef[ProviderJob.Command]) extends Command
  case class RemoveProvider(zipCode: String, country: Option[String]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    val jobMapper: ActorRef[ProviderJob.Command] = ctx.messageAdapter[ProviderJob.Command] {
      case ProviderJob.JobEnded(zipCode, country) => RemoveProvider(zipCode, country)
    }

    def run(jobs: Map[(String, Option[String]), ActorRef[ProviderJob.Command]]): Behavior[Command] = Behaviors.receiveMessage {
      case RequestProviders(zipCode, country, actorRef) =>
        jobs.get((zipCode, country)).fold {
          val job: ActorRef[ProviderJob.Command] =
            ctx.spawn(
              ProviderJob(zipCode, country, actorRef, jobMapper),
              s"$zipCode-${country.getOrElse("USA")}"
            )

          run(jobs + ((zipCode, country) -> job))
        } { job =>
          job ! ProviderJob.AddListener(actorRef)
          Behaviors.same
        }
      case RemoveProvider(zipCode, country) =>
        run(jobs.removed((zipCode, country)))
    }

    run(Map())
  }

}
