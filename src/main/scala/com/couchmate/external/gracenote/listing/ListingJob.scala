package com.couchmate.external.gracenote.listing

import java.time.{LocalDateTime, ZoneId}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.couchmate.api.models.grid.Grid
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.{GridDAO, ProviderDAO}
import com.couchmate.data.models.Provider
import com.couchmate.external.gracenote._
import com.couchmate.external.gracenote.models.{GracenoteChannelAiring, GracenoteSport}
import com.couchmate.util.akka.extensions.DatabaseExtension
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

    ctx.pipeToSelf(getProvider(providerId)) {
      case Success(Some(value)) => ProviderSuccess(value)
      case Success(None) => ProviderFailure(new RuntimeException("Cant find provider"))
      case Failure(exception) => ProviderFailure(exception)
    }

    ctx.pipeToSelf(getSports) {
      case Success(value) => SportsSuccess(value)
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
        .flatMapConcat(
          listings(
            state.provider.extId,
            _
          )
        )
        .flatMapConcat(slotAiring =>
          channel(
            state.provider.providerId.get,
            slotAiring.channelAiring,
            slotAiring.slot
          )
        )
        .flatMapConcat(plan => {
          Source.fromIterator(() => (plan.add.map(airing =>
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
        })
        .flatMapMerge(10, identity)
        .run
        .flatMap(_ => getGrid(providerId, LocalDateTime.now(ZoneId.of("UTC")), 60))
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

    def getSports: Future[Seq[GracenoteSport]] =
      for {
        response <- http.singleRequest(makeGracenoteRequest(
          config.getString("gracenote.host"),
          config.getString("gracenote.apiKey"),
          Seq("sports", "all"),
          Map(
            "includeOrg" -> Some("true"),
            "officialOrg" -> Some("true")
          )
        ))
        decoded <- Gzip.decodeMessage(response).toStrict(5 seconds)
        sports <- Unmarshal(decoded.entity).to[Seq[GracenoteSport]]
      } yield sports

    start(PreState())
  }
}
