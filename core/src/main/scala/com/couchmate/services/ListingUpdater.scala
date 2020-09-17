package com.couchmate.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.adapter._
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.couchmate.common.dao.{ProviderDAO, UserProviderDAO}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.services.gracenote.listing.{ListingJob, ListingPullType}
import com.couchmate.util.akka.extensions.{DatabaseExtension}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object ListingUpdater
  extends ProviderDAO
  with UserProviderDAO {
  sealed trait Command

  private final case class AddProviders(providers: Seq[Long]) extends Command
  private final case class FailedProviders(err: Throwable) extends Command
  private final case object StartUpdate extends Command
  private final case object ColdStart extends Command
  private final case class StartJob(providerId: Long) extends Command
  private final case class JobAlive(providerId: Long) extends Command
  private final case class JobDead(providerId: Long) extends Command
  private final case class JobFinished(providerId: Long) extends Command

  private sealed trait Event

  private final case class ProvidersAdded(providers: Seq[Long]) extends Event
  private final case class ProviderCompleted(providerId: Long) extends Event
  private final case class ProviderStarted(providerId: Long, actorRef: ActorRef[ListingJob.Command]) extends Event
  private final case class ProviderFailed(err: Throwable) extends Event

  private final case class CurrentJob(
    providerId: Long,
    actorRef: ActorRef[ListingJob.Command]
  )

  private final case class State(
    jobs: List[Long],
    currentJob: Option[CurrentJob]
  )

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db
    implicit val timeout: Timeout = 5 seconds

    val scheduler: QuartzSchedulerExtension = QuartzSchedulerExtension(ctx.system.toClassic)

    val jobMapper: ActorRef[ListingJob.Command] = ctx.messageAdapter[ListingJob.Command] {
      case ListingJob.JobEnded(providerId, _) => JobFinished(providerId)
      case ListingJob.JobFailed(providerId, _) => JobDead(providerId)
    }

    scheduler.schedule(
      "EveryOtherDay",
      ctx.self.toClassic,
      StartUpdate,
      None
    )

    ctx.self ! ColdStart

    def commandHandler: (State, Command) => Effect[Event, State] = {
      (_, command) => command match {
        case ColdStart => Effect.none
            .thenRun((s: State) => s.jobs.headOption.fold(()) { providerId =>
              ctx.self ! StartJob(providerId)
            })
        case StartUpdate => Effect.none
          .thenRun((_: State) => ctx.pipeToSelf(getProviders) {
            case Success(value) => AddProviders(value)
            case Failure(exception) => FailedProviders(exception)
          })
        case AddProviders(providers) =>
          ctx.log.info(s"Pulled jobs ${providers.mkString(", ")}")
          Effect
            .persist(ProvidersAdded(providers))
            .thenRun((s: State) => s.currentJob.fold({
              ctx.log.info(s"Starting ${s.jobs.head}, remaining: ${s.jobs.tail.mkString(", ")}")
              ctx.self ! StartJob(s.jobs.head)
            })(job => {
              ctx.log.info(s"Found job ${job.providerId}, making sure its still running")
              ctx.ask(job.actorRef, ListingJob.Ping){
                case Success(ListingJob.Pong(providerId)) => JobAlive(providerId)
                case Failure(_) => JobDead(job.providerId)
              }
            }))
        case JobAlive(_) => Effect.none
        case JobDead(providerId) =>
          ctx.log.info(s"Job $providerId was dead, restarting.")
          ctx.self ! StartJob(providerId)
          Effect.none
        case JobFinished(providerId) =>
          Effect.persist(ProviderCompleted(providerId))
            .thenRun((s: State) => s.jobs.headOption.fold(()){ nextProvider =>
              ctx.log.info(s"Starting $nextProvider, remaining: ${s.jobs.tail.mkString(", ")}")
              ctx.self ! StartJob(nextProvider)
            })
        case StartJob(providerId) =>
          val job = ctx.spawnAnonymous(ListingJob(
            providerId,
            ListingPullType.Full,
            ctx.system.ignoreRef,
            jobMapper
          ))
          Effect.persist(ProviderStarted(providerId, job))
        case FailedProviders(ex) =>
          ctx.log.error(s"Failed to get providers", ex)
          Effect.unhandled
      }
    }

    def eventHandler: (State, Event) => State =
      (state, event) => event match {
        case ProvidersAdded(providers) =>
          state.copy(
            jobs = state.jobs ++ providers
          )
        case ProviderCompleted(providerId) =>
          if (state.jobs.nonEmpty) {
            state.copy(
              jobs = state.jobs.tail
            )
          } else {
            state.copy(
              jobs = List.empty
            )
          }
        case ProviderStarted(providerId, actorRef) => state.copy(
          currentJob = Some(CurrentJob(
            providerId, actorRef
          ))
        )
      }

    EventSourcedBehavior(
      PersistenceId.ofUniqueId("ListingUpdater"),
      State(List.empty, Option.empty),
      commandHandler,
      eventHandler
    )
  }

  def getProviders()(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[Long]] = for {
    defaults <- getProvidersForType("Default")
    users <- getUniqueProviders()
  } yield (defaults ++ users).map(_.providerId.get).distinct
}
