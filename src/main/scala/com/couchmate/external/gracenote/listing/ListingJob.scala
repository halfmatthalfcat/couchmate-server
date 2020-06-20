package com.couchmate.external.gracenote.listing

import java.time.{LocalDateTime, ZoneId}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorFlow
import akka.util.Timeout
import com.couchmate.api.models.grid.Grid
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.{GridDAO, ProviderDAO}
import com.couchmate.data.models.{Lineup, Provider}
import com.couchmate.external.gracenote._
import com.couchmate.external.gracenote.models.{GracenoteAiringPlanResult, GracenoteChannelAiring, GracenoteSlotAiring, GracenoteSport}
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
  with GridDAO {
  sealed trait Command

  final case class AddListener(actorRef: ActorRef[Command]) extends Command
  final case class JobEnded(providerId: Long, grid: Grid) extends Command

  private final case class ProviderSuccess(provider: Provider) extends Command
  private final case class ProviderFailure(err: Throwable) extends Command

  private final case class SportsSuccess(sports: Seq[GracenoteSport]) extends Command
  private final case class SportsFailure(err: Throwable) extends Command

  private final case class GridSuccess(grid: Grid) extends Command
  private final case class GridFailure(err: Throwable) extends Command

  private final case class RequestSuccess(
    slot: LocalDateTime,
    channelAirings: Seq[GracenoteChannelAiring]
  ) extends Command
  private final case class RequestFailure(err: Throwable) extends Command

  private final case class PreState(
    provider: Option[Provider] = None,
    sports: Option[Seq[GracenoteSport]] = None
  )

  private final case class JobState(
    provider: Provider,
    sports: Seq[GracenoteSport],
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

    def start(state: PreState): Behavior[Command] = Behaviors.receiveMessage {
      case ProviderSuccess(provider) =>
        if (state.sports.nonEmpty) {
          run(JobState(
            provider = provider,
            sports = state.sports.get
          ))
        } else {
          start(state.copy(
            provider = Some(provider)
          ))
        }
      case SportsSuccess(sports) =>
        if (state.provider.nonEmpty) {
          run(JobState(
            provider = state.provider.get,
            sports = sports
          ))
        } else {
          start(state.copy(
            sports = Some(sports)
          ))
        }

      case ProviderFailure(err) =>
        ctx.log.error(s"Unable to get provider", err)
        Behaviors.stopped
      case SportsFailure(err) =>
        ctx.log.error(s"Unable to get sports", err)
        Behaviors.stopped
    }

    def run(state: JobState): Behavior[Command] = Behaviors.setup { ctx =>
      import ListingStreams._

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
               case (acc, lineup) if lineup.active => acc.copy(added = acc.added + 1)
               case (acc, lineup) if !lineup.active => acc.copy(removed = acc.removed + 1)
               case (acc, _) => acc
             }
           }).reduce((prev: GracenoteAiringPlanResult, curr: GracenoteAiringPlanResult) => prev.copy(
             added = prev.added + curr.added,
             removed = prev.removed + curr.removed,
             skipped = prev.skipped + curr.skipped
           )).log(s"${providerId}")
        )
        .run
        .flatMap(_ => getGrid(providerId))
        .onComplete {
          case Success(value) =>
            ctx.self ! JobEnded(providerId, value)
        }

      def job(listeners: Seq[ActorRef[Command]]): Behavior[Command] = Behaviors.receiveMessage {
        case AddListener(listener) => job(listeners :+ listener)
        case ended: JobEnded =>
          listeners.foreach(_ ! ended)
          parent ! ended
          Behaviors.stopped
      }

      job(Seq(initiate))
    }

    start(PreState())
  }
}
