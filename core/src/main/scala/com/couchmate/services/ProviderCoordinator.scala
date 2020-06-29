package com.couchmate.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.common.dao.ZipProviderDAO
import com.couchmate.common.models.api.Provider
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.services.gracenote.provider.ProviderJob
import com.couchmate.util.akka.extensions.DatabaseExtension
import com.neovisionaries.i18n.CountryCode

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ProviderCoordinator
  extends ZipProviderDAO {
  sealed trait Command

  final case class RequestProviders(
    zipCode: String,
    country: CountryCode,
    senderRef: ActorRef[ProviderJob.Command]
  ) extends Command
  final case class RemoveProvider(
    zipCode: String,
    country: CountryCode,
    providers: Seq[Provider]
  ) extends Command

  private final case class ZipProvidersSuccess(zipProviders: Map[(String, CountryCode), Seq[Provider]]) extends Command
  private final case class ZipProvidersFailure(err: Throwable) extends Command

  private final case class State(
    zipProviders: Map[(String, CountryCode), Seq[Provider]] = Map(),
    jobs: Map[(String, CountryCode), ActorRef[ProviderJob.Command]] = Map()
  )

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    val jobMapper: ActorRef[ProviderJob.Command] = ctx.messageAdapter[ProviderJob.Command] {
      case ProviderJob.JobEnded(zipCode, country, providers) => RemoveProvider(zipCode, country, providers)
      case ProviderJob.JobFailure(zipCode, country, _) => RemoveProvider(zipCode, country, Seq())
    }

    ctx.pipeToSelf(getZipMap) {
      case Success(value) => ZipProvidersSuccess(
        value
          .groupBy(dzp => (dzp.zipCode, dzp.countryCode))
          .view
          .mapValues(_.map(zpd => Provider(
            zpd.providerId,
            zpd.name,
            zpd.`type`,
            zpd.location
          )))
          .toMap
      )
      case Failure(exception) => ZipProvidersFailure(exception)
    }

    def run(state: State): Behavior[Command] = Behaviors.receiveMessage {
      case ZipProvidersSuccess(zipProviders) =>
        run(state.copy(
          zipProviders = zipProviders
        ))
      case ZipProvidersFailure(_) =>
        Behaviors.same
      case RequestProviders(zipCode, country, actorRef) =>
        val cached: Boolean =
          state.zipProviders.contains((zipCode, country))
        if (cached) {
          actorRef ! ProviderJob.JobEnded(
            zipCode,
            country,
            state.zipProviders((zipCode, country))
          )
          Behaviors.same
        } else {
          state.jobs.get((zipCode, country)).fold {
            val job: ActorRef[ProviderJob.Command] =
              ctx.spawn(
                ProviderJob(zipCode, country, actorRef, jobMapper),
                s"$zipCode-${country.getAlpha3}"
              )

            run(state.copy(
              jobs = state.jobs + ((zipCode, country) -> job)
            ))
          } { job =>
            job ! ProviderJob.AddListener(actorRef)
            Behaviors.same
          }
        }
      case RemoveProvider(zipCode, country, providers) =>
        run(state.copy(
          zipProviders = state.zipProviders + ((zipCode, country) -> providers),
          jobs = state.jobs.removed((zipCode, country))
        ))
    }

    run(State())
  }

}
