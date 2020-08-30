package com.couchmate.services.gracenote.listing

import java.time.{LocalDateTime, ZoneId}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.typed.scaladsl.ActorFlow
import akka.util.Timeout
import com.couchmate.common.dao.{GridDAO, ListingJobDAO, ProviderDAO}
import com.couchmate.common.models.api.grid.Grid
import com.couchmate.common.models.thirdparty.gracenote.{GracenoteAiringPlanResult, GracenoteChannelAiring, GracenoteSlotAiring, GracenoteSport}
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{Lineup, ListingJobStatus, Provider, ListingJob => ListingJobModel}
import com.couchmate.common.util.DateUtils
import com.couchmate.services.GracenoteCoordinator
import com.couchmate.util.akka.extensions.{DatabaseExtension, SingletonExtension}
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ListingJob
  extends PlayJsonSupport
  with ProviderDAO
  with GridDAO
  with ListingJobDAO {
  sealed trait Command

  final case class AddListener(actorRef: ActorRef[Command]) extends Command
  final case class JobEnded(providerId: Long, grid: Grid) extends Command
  final case class JobFailed(providerId: Long, err: Throwable) extends Command

  final case class Ping(actorRef: ActorRef[Command]) extends Command
  final case class Pong(providerId: Long) extends Command

  private final case class ProviderSuccess(provider: Provider) extends Command
  private final case class ProviderFailure(err: Throwable) extends Command

  private final case class SportsSuccess(sports: Seq[GracenoteSport]) extends Command
  private final case class SportsFailure(err: Throwable) extends Command

  private final case class JobSuccess(job: ListingJobModel) extends Command
  private final case class JobFailure(err: Throwable) extends Command

  private final case class GridSuccess(grid: Grid) extends Command
  private final case class GridFailure(err: Throwable) extends Command

  private final case class RequestSuccess(
    slot: LocalDateTime,
    channelAirings: Seq[GracenoteChannelAiring]
  ) extends Command
  private final case class RequestFailure(err: Throwable) extends Command

  private final case class PreState(
    provider: Option[Provider] = None,
    sports: Option[Seq[GracenoteSport]] = None,
    job: Option[ListingJobModel] = None,
  )

  private final case class JobState(
    provider: Provider,
    sports: Seq[GracenoteSport],
    job: ListingJobModel
  )

  def apply(
    providerId: Long,
    pullType: ListingPullType,
    initiate: ActorRef[Command],
    parent: ActorRef[Command],
  ): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.debug(s"Starting job $providerId")
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = Materializer(ctx)
    implicit val db: Database = DatabaseExtension(ctx.system).db
    implicit val http: HttpExt = Http(ctx.system)
    implicit val config: Config = ConfigFactory.load()
    implicit val timeout: Timeout = 30 seconds

    val gracenoteCoordinator: ActorRef[GracenoteCoordinator.Command] =
      SingletonExtension(ctx.system).gracenoteCoordinator

    ctx.pipeToSelf(getProvider(providerId)) {
      case Success(Some(value)) => ProviderSuccess(value)
      case Success(None) => ProviderFailure(new RuntimeException("Cant find provider"))
      case Failure(exception) => ProviderFailure(exception)
    }

    ctx.ask(gracenoteCoordinator, GracenoteCoordinator.GetSports) {
      case Success(GracenoteCoordinator.GetSportsSuccess(sports)) => SportsSuccess(sports)
      case Success(GracenoteCoordinator.GetSportsFailure(err)) => SportsFailure(err)
      case Failure(exception) => SportsFailure(exception)
    }

    ctx.pipeToSelf(upsertListingJob(ListingJobModel(
      providerId = providerId,
      pullAmount = pullType.value,
      started = LocalDateTime.now(ZoneId.of("UTC")),
      baseSlot = DateUtils.roundNearestHour(LocalDateTime.now(ZoneId.of("UTC"))),
      status = ListingJobStatus.InProgress
    ))) {
      case Success(job) => JobSuccess(job)
      case Failure(exception) => JobFailure(exception)
    }

    def start(state: PreState): Behavior[Command] = Behaviors.receiveMessage {
      case ProviderSuccess(provider) =>
        if (state.sports.nonEmpty && state.job.nonEmpty) {
          run(JobState(
            provider = provider,
            sports = state.sports.get,
            job = state.job.get,
          ))
        } else {
          start(state.copy(
            provider = Some(provider)
          ))
        }
      case SportsSuccess(sports) =>
        if (state.provider.nonEmpty && state.job.nonEmpty) {
          run(JobState(
            provider = state.provider.get,
            sports = sports,
            job = state.job.get,
          ))
        } else {
          start(state.copy(
            sports = Some(sports)
          ))
        }
      case JobSuccess(job) =>
        if (state.provider.nonEmpty && state.sports.nonEmpty) {
          run(JobState(
            provider = state.provider.get,
            sports = state.sports.get,
            job = state.job.get
          ))
        } else {
          start(state.copy(
            job = Some(job)
          ))
        }

      case ProviderFailure(err) =>
        ctx.log.error(s"Unable to get provider", err)
        parent ! JobFailed(providerId, err)
        Behaviors.stopped
      case SportsFailure(err) =>
        ctx.log.error(s"Unable to get sports", err)
        parent ! JobFailed(providerId, err)
        Behaviors.stopped
      case JobFailure(err) =>
        ctx.log.error(s"Unable to make job", err)
        parent ! JobFailed(providerId, err)
        Behaviors.stopped
    }

    def run(state: JobState): Behavior[Command] = Behaviors.setup { ctx =>
      import ListingStreams._
      // TODO tweak this, we were seeing ask timeouts to GNCoordinator
      // May be a product of weak nodes and just not enough time for it to respond
      implicit val timeout: Timeout = 30 minutes

      slots(pullType)
        .via(ActorFlow.ask[
          LocalDateTime,
          GracenoteCoordinator.Command,
          GracenoteCoordinator.Command
        ](
          gracenoteCoordinator
        )((slot, actorRef) => GracenoteCoordinator.GetListings(
          state.provider.extId,
          slot,
          actorRef
        ))
        .flatMapConcat {
          case GracenoteCoordinator.GetListingsSuccess(slot, listings) =>
            Source.fromIterator(() => listings.map(GracenoteSlotAiring(
              slot,
              _
            )).iterator)
          case GracenoteCoordinator.GetListingsFailure(ex) =>
            Source.failed(ex)
        }.flatMapConcat(slotAiring =>
          channel(
            state.provider.providerId.get,
            slotAiring.channelAiring,
            slotAiring.slot
          )).flatMapConcat(plan => {
           Source
             .fromIterator(() => (plan.add.map(airing =>
               lineup(
                 plan.providerChannelId,
                 airing,
                 state.sports
               )
             ) ++ plan.remove.map(airing =>
               disable(
                 plan.providerChannelId,
                 airing
               )
             )).iterator)
             .flatMapMerge(10, identity)
             .fold(GracenoteAiringPlanResult(
               plan.startTime,
               0, 0, plan.skip.size
             )) {
               case (acc, lineup: Lineup) if lineup.active => acc.copy(added = acc.added + 1)
               case (acc, lineup: Lineup) if !lineup.active => acc.copy(removed = acc.removed + 1)
               case (acc, _) => acc
             }
           })
          // Side-effect to update job
          .alsoTo(Sink.foreachAsync(1)(plan => upsertListingJob(
            state.job.copy(
              lastSlot = Some(plan.startTime)
            )
          ).map(_ => ())))
          .reduce((prev: GracenoteAiringPlanResult, curr: GracenoteAiringPlanResult) => prev.copy(
           added = prev.added + curr.added,
           removed = prev.removed + curr.removed,
           skipped = prev.skipped + curr.skipped
         ))
          .mapAsync(1)(plan => upsertListingJob(
            state.job.copy(
              completed = Some(LocalDateTime.now(ZoneId.of("UTC"))),
              status = ListingJobStatus.Complete,
              lastSlot = Some(plan.startTime.plusHours(pullType.value))
            )))
        )
        .run
        .flatMap(_ => getGrid(providerId))
        .onComplete {
          case Success(value) =>
            ctx.self ! JobEnded(providerId, value)
          case Failure(exception) =>
            ctx.log.error(s"Listing job for $providerId failed", exception)
            upsertListingJob(
              state.job.copy(
                completed = Some(LocalDateTime.now(ZoneId.of("UTC"))),
                status = ListingJobStatus.Error
              )
            )
            ctx.self ! JobFailure(exception)
        }

      def job(listeners: Seq[ActorRef[Command]]): Behavior[Command] = Behaviors.receiveMessage {
        case Ping(actorRef) =>
          actorRef ! Pong(providerId)
          Behaviors.same
        case AddListener(listener) =>
          job(listeners :+ listener)
        case ended: JobEnded =>
          listeners.foreach(_ ! ended)
          parent ! ended
          Behaviors.stopped
        case JobFailure(err) =>
          parent ! JobFailed(providerId, err)
          Behaviors.stopped
      }

      job(Seq(initiate))
    }

    start(PreState())
  }
}
