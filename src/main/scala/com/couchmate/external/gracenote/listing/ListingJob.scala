package com.couchmate.external.gracenote.listing

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.{GridDAO, ProviderDAO}
import com.couchmate.data.models.Provider
import com.couchmate.external.gracenote._
import com.couchmate.external.gracenote.models.GracenoteChannelAiring
import com.couchmate.util.DateUtils
import com.couchmate.util.akka.extensions.DatabaseExtension
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ListingJob
  extends PlayJsonSupport
  with GridDAO
  with ProviderDAO {
  sealed trait Command

  final case class AddListener(actorRef: ActorRef[Command]) extends Command
  final case class JobEnded(providerId: Long) extends Command

  private final case class ProviderSuccess(provider: Provider) extends Command
  private final case class ProviderFailure(err: Throwable) extends Command

  private final case class RequestSuccess(
    slot: LocalDateTime,
    channelAirings: Seq[GracenoteChannelAiring]
  ) extends Command
  private final case class RequestFailure(err: Throwable) extends Command

  private final case class JobState(
    listeners: Seq[ActorRef[Command]] = Seq(),
    added: Int = 0,
    removed: Int = 0,
    failures: Int = 0,
    totalChanges: Int = 0
  ) {
    def isDone: Boolean =
      (added + removed + failures) == totalChanges
  }
  private final case object Added extends Command
  private final case object Removed extends Command
  private final case object Failed extends Command
  private final case class AddToTotal(total: Int) extends Command

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
    val http: HttpExt = Http(ctx.system)
    val config: Config = ConfigFactory.load()
    val gnApiKey: String = config.getString("gracenote.apiKey")
    val gnHost: String = config.getString("gracenote.host")

    val channelAiringIngesterAdapter = ctx.messageAdapter[ChannelAiringIngester.Command] {
      case ChannelAiringIngester.Added => Added
      case ChannelAiringIngester.Removed => Removed
      case ChannelAiringIngester.Failed => Failed
      case ChannelAiringIngester.TotalChanges(changes) => AddToTotal(changes)
    }

    ctx.pipeToSelf(getProvider(providerId)) {
      case Success(Some(value)) => ProviderSuccess(value)
      case Failure(exception) => ProviderFailure(exception)
    }

    def run(state: JobState): Behavior[Command] = Behaviors.receiveMessage {
      case ProviderSuccess(provider) => for (i <- 0 to pullType.value) {
        val slot: LocalDateTime = DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC")).plusHours(i)
        )
        ctx.pipeToSelf(getListing(provider, slot)) {
          case Success(value) => RequestSuccess(slot, value)
          case Failure(exception) => RequestFailure(exception)
        }
      }
        Behaviors.same
      case RequestSuccess(slot, channelAirings) =>
        channelAirings.foreach(channelAiring =>
          ctx.spawnAnonymous(ChannelAiringIngester(
            providerId,
            slot,
            channelAiring,
            channelAiringIngesterAdapter
          ))
        )
        Behaviors.same
      case AddListener(listener) =>
        run(state.copy(
          listeners = state.listeners :+ listener
        ))
      case Added =>
        val newState = state.copy(
          added = state.added + 1
        )
        if (newState.isDone) {
          parent ! JobEnded(providerId)
          Behaviors.stopped
        } else {
          run(newState)
        }
      case Removed =>
        val newState = state.copy(
          removed = state.removed + 1
        )
        if (newState.isDone) {
          parent ! JobEnded(providerId)
          Behaviors.stopped
        } else {
          run(newState)
        }
      case Failed =>
        val newState = state.copy(
          failures = state.failures + 1
        )
        if (newState.isDone) {
          parent ! JobEnded(providerId)
          Behaviors.stopped
        } else {
          run(newState)
        }
      case AddToTotal(total) =>
        run(state.copy(
          totalChanges = state.totalChanges + total
        ))
    }

    def getListing(
      provider: Provider,
      slot: LocalDateTime
    ): Future[Seq[GracenoteChannelAiring]] =
      for {
        response <- http.singleRequest(makeGracenoteRequest(
          gnHost,
          gnApiKey,
          Seq("lineups", provider.extId, "grid"),
          Map(
            "startDateTime" -> Some(
              slot.format(DateTimeFormatter.ISO_DATE_TIME)
            ),
            "endDateTime" -> Some(
              slot.plusHours(1).format(DateTimeFormatter.ISO_DATE_TIME)
            )
          )
        ))
        decoded = Gzip.decodeMessage(response)
        channelAirings <- Unmarshal(decoded.entity).to[Seq[GracenoteChannelAiring]]
      } yield channelAirings

    run(JobState(
      listeners = Seq(initiate)
    ))
  }
}
