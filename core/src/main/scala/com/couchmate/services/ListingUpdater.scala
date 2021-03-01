package com.couchmate.services

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.adapter._
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.couchmate.common.dao.{ProviderDAO, UserProviderDAO}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.services.gracenote.listing.{ListingJob, ListingPullType}
import com.couchmate.util.akka.extensions.DatabaseExtension
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object ListingUpdater
  extends ProviderDAO
  with UserProviderDAO {
  sealed trait Command

  private final case class AddJobs(providers: Seq[Job]) extends Command
  private final case class FailedProviders(err: Throwable) extends Command
  private final case object StartUpdate extends Command
  private final case object StartRefresh extends Command
  private final case object StartShortRefresh extends Command
  private final case object ColdStart extends Command
  private final case class StartJob(job: Job) extends Command
  private final case class JobAlive(jobId: UUID) extends Command
  private final case class JobDead(jobId: UUID) extends Command
  private final case class JobFinished(jobId: UUID) extends Command

  final case class StartJobRemote(jobType: ListingPullType, ref: ActorRef[Command]) extends Command
  final case class StartJobRemoteResult(queue: Seq[(Long, Int)]) extends Command

  private sealed trait Event

  private final case class JobsAdded(jobs: Seq[Job]) extends Event
  private final case class JobCompleted(jobId: UUID) extends Event
  private final case class JobStarted(job: CurrentJob) extends Event
  private final case class ProviderFailed(err: Throwable) extends Event

  private final case class Job(
    jobId: UUID,
    providerId: Long,
    jobType: ListingPullType,
  )

  private final case class CurrentJob(
    jobId: UUID,
    providerId: Long,
    jobType: ListingPullType,
    actorRef: ActorRef[ListingJob.Command]
  )

  private final case class State(
    jobs: List[Job],
    currentJob: Option[CurrentJob]
  )

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info(s"ListingUpdater started")
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db
    implicit val timeout: Timeout = 5 seconds

    val scheduler: QuartzSchedulerExtension =
      QuartzSchedulerExtension(ctx.system.toClassic)

    val jobMapper: ActorRef[ListingJob.Command] = ctx.messageAdapter[ListingJob.Command] {
      case ListingJob.JobEnded(jobId, _, _) => JobFinished(jobId)
      case ListingJob.JobFailed(jobId, _, _) => JobDead(jobId)
    }

    scheduler.schedule(
      "EverySunday",
      ctx.self.toClassic,
      StartUpdate,
      None
    )

    scheduler.schedule(
      "EveryDayExceptSunday",
      ctx.self.toClassic,
      StartRefresh,
      None
    )

    ctx.self ! ColdStart

    def commandHandler: (State, Command) => Effect[Event, State] = {
      (_, command) => command match {
        case ColdStart => Effect.none
            .thenRun((s: State) => s.jobs.headOption.fold(()) { job =>
              ctx.self ! StartJob(job)
            })
        case StartJobRemote(jobType, ref) =>
          Effect.none
                .thenRun((_: State) => ctx.pipeToSelf(getProviders) {
                  case Success(value) => AddJobs(value.map(providerId => Job(
                    UUID.randomUUID(),
                    providerId,
                    jobType
                  )))
                  case Failure(exception) => FailedProviders(exception)
                }).thenReply(ref)({
            case State(jobs, _) => StartJobRemoteResult(
              jobs.map(job => job.providerId -> job.jobType.value)
            )
          })
        case StartUpdate =>
          ctx.log.info(s"Starting update job (pulling for week)")
          Effect.none
            .thenRun((_: State) => ctx.pipeToSelf(getProviders) {
              case Success(value) => AddJobs(value.map(providerId => Job(
                UUID.randomUUID(),
                providerId,
                ListingPullType.Week
              )))
              case Failure(exception) => FailedProviders(exception)
            })
        case StartRefresh =>
          ctx.log.info(s"Starting refresh job (pulling for day)")
          Effect.none
            .thenRun((_: State) => ctx.pipeToSelf(getProviders) {
              case Success(value) => AddJobs(value.map(providerId => Job(
                UUID.randomUUID(),
                providerId,
                ListingPullType.Day
              )))
              case Failure(exception) => FailedProviders(exception)
            })
        case StartShortRefresh =>
          ctx.log.info(s"Starting refresh job (pulling for 6 hours)")
          Effect.none
            .thenRun((_: State) => ctx.pipeToSelf(getProviders) {
              case Success(value) => AddJobs(value.map(providerId => Job(
                UUID.randomUUID(),
                providerId,
                ListingPullType.SixHours
              )))
              case Failure(exception) => FailedProviders(exception)
            })
        case AddJobs(jobs) =>
          ctx.log.info(s"Pulled jobs ${jobs.map(job => s"${job.providerId} (${job.jobType})").mkString(", ")}")
          Effect
            .persist(JobsAdded(jobs))
            .thenRun((s: State) => s.currentJob.fold({
              ctx.log.info(s"Starting ${s.jobs.head}, remaining: ${s.jobs.tail.mkString(", ")}")
              ctx.self ! StartJob(s.jobs.head)
            })(job => {
              ctx.log.info(s"Found job ${job.providerId}, making sure its still running")
              ctx.ask(job.actorRef, ListingJob.Ping){
                case Success(ListingJob.Pong(providerId)) => JobAlive(job.jobId)
                case Failure(_) => JobDead(job.jobId)
              }
            }))
        case JobAlive(_) => Effect.none
        case JobDead(jobId) =>
          Effect.none.thenRun((s: State) => {
            ctx.log.info(s"Job $jobId was dead, restarting.")
            s.currentJob.fold(()) { currentJob =>
              ctx.self ! StartJob(Job(
                currentJob.jobId,
                currentJob.providerId,
                currentJob.jobType
              ))
            }
          })
        case JobFinished(jobId) =>
          Effect.persist(JobCompleted(jobId))
            .thenRun((s: State) => s.jobs.headOption.fold(()){ nextJob =>
              ctx.log.info(s"Starting ${nextJob.providerId} (${nextJob.jobType}), remaining jobs: ${s.jobs.tail.size}")
              ctx.self ! StartJob(nextJob)
            })
        case StartJob(nextJob) =>
          val job = ctx.spawnAnonymous(ListingJob(
            nextJob.jobId,
            nextJob.providerId,
            nextJob.jobType,
            ctx.system.ignoreRef,
            jobMapper
          ))
          Effect.persist(JobStarted(CurrentJob(
            nextJob.jobId,
            nextJob.providerId,
            nextJob.jobType,
            job
          )))
        case FailedProviders(ex) =>
          ctx.log.error(s"Failed to get providers", ex)
          Effect.unhandled
      }
    }

    def eventHandler: (State, Event) => State =
      (state, event) => event match {
        case JobsAdded(jobs) =>
          state.copy(
            jobs = state.jobs ++ jobs
          )
        case JobCompleted(jobId) =>
          state.copy(
            jobs = state.jobs.filterNot(_.jobId == jobId),
            currentJob = Option.empty,
          )
        case JobStarted(job) => state.copy(
          currentJob = Some(job)
        )
      }

    EventSourcedBehavior(
      PersistenceId.ofUniqueId("ListingUpdater"),
      State(List.empty, Option.empty),
      commandHandler,
      eventHandler
    ).withRetention(
      RetentionCriteria.snapshotEvery(
        numberOfEvents = 50, keepNSnapshots = 2
      ).withDeleteEventsOnSnapshot
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
