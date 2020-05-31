package com.couchmate.external.gracenote.listing

import java.time.LocalDateTime

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.{ChannelDAO, ListingCacheDAO, ProviderChannelDAO, ProviderDAO}
import com.couchmate.data.models.{Channel, ListingCache, ProviderChannel}
import com.couchmate.external.gracenote.models.{GracenoteAiring, GracenoteChannelAiring}
import com.couchmate.util.akka.extensions.DatabaseExtension

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ChannelAiringIngester
  extends ProviderDAO
  with ChannelDAO
  with ListingCacheDAO
  with ProviderChannelDAO {
  sealed trait Command

  final case object Complete extends Command

  final case object Added extends Command
  final case object Removed extends Command
  final case object Failed extends Command
  final case class TotalChanges(changes: Int) extends Command

  private final case class ChannelSuccess(channel: Option[Channel]) extends Command
  private final case class ChannelFailure(err: Throwable) extends Command

  private final case class ProviderChannelSuccess(providerChannel: Option[ProviderChannel]) extends Command
  private final case class ProviderChannelFailure(err: Throwable) extends Command

  private final case class CacheSuccess(cache: Option[ListingCache]) extends Command
  private final case class CacheFailure(err: Throwable) extends Command

  private final case class Ingested(gnAiring: GracenoteAiring) extends Command
  private final case class IngestFailure(
    gnAiring: GracenoteAiring,
    err: Throwable
  ) extends Command

  private final case class Disabled(gnAiring: GracenoteAiring) extends Command
  private final case class DisableFailure(
    gnAiring: GracenoteAiring,
    err: Throwable
  ) extends Command

  private final case class PlanStatus(
    added: Seq[GracenoteAiring] = Seq(),
    removed: Seq[GracenoteAiring] = Seq(),
    failures: Seq[GracenoteAiring] = Seq(),
  ) extends Command

  private final case class AiringPlan(
    add: Seq[GracenoteAiring],
    remove: Seq[GracenoteAiring],
    skip: Seq[GracenoteAiring]
  )

  def apply(
    providerId: Long,
    slot: LocalDateTime,
    channelAiring: GracenoteChannelAiring,
    senderRef: ActorRef[Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    ctx.pipeToSelf(getChannelForExt(channelAiring.stationId)) {
      case Success(value) => ChannelSuccess(value)
      case Failure(exception) => ChannelFailure(exception)
    }

    def start(): Behavior[Command] = Behaviors.receiveMessage {
      case ChannelSuccess(Some(value)) =>
        ctx.pipeToSelf(getProviderChannelForProviderAndChannel(
          providerId,
          value.channelId.get,
        )) {
          case Success(value) => ProviderChannelSuccess(value)
          case Failure(exception) => ProviderChannelFailure(exception)
        }
        Behaviors.same
      case ProviderChannelSuccess(Some(value)) =>
        ctx.pipeToSelf(getListingCache(
          value.providerChannelId.get,
          slot,
        )) {
          case Success(value) => CacheSuccess(value)
          case Failure(exception) => CacheFailure(exception)
        }
        withProviderChannel(value)
    }

    def withProviderChannel(
      providerChannel: ProviderChannel
    ): Behavior[Command] = Behaviors.receiveMessagePartial {
      case CacheSuccess(Some(value)) =>
        commit(
          providerChannel,
          getPlan(value.airings, channelAiring.airings)
        )
      case CacheSuccess(None) =>
        ctx.pipeToSelf(upsertListingCache(ListingCache(
          listingCacheId = None,
          providerChannel.providerChannelId.get,
          slot,
          channelAiring.airings
        ))) {
          // Clear out airing for the initial commit
          case Success(value) => CacheSuccess(Some(value.copy(airings = Seq())))
          case Failure(exception) => CacheFailure(exception)
        }
        Behaviors.same
    }

    def commit(
      providerChannel: ProviderChannel,
      plan: AiringPlan
    ): Behavior[Command] = {
      val lineupAdapter = ctx.messageAdapter[LineupIngester.Command] {
        case LineupIngester.Ingested(lineup) => Ingested(lineup)
        case LineupIngester.IngestFailed(gnAiring, err) => IngestFailure(gnAiring, err)
      }

      val disableAdapter = ctx.messageAdapter[LineupDisabler.Command] {
        case LineupDisabler.Disabled(gracenoteAiring) => Disabled(gracenoteAiring)
        case LineupDisabler.DisableFailure(gnAiring, err) => DisableFailure(gnAiring, err)
      }

      plan.add.foreach(airing => ctx.spawnAnonymous(LineupIngester(
        airing,
        providerChannel,
        lineupAdapter
      )))

      plan.remove.foreach(airing => ctx.spawnAnonymous(LineupDisabler(
        airing,
        providerChannel,
        disableAdapter
      )))

      senderRef ! TotalChanges(plan.add.size + plan.remove.size)

      def run(status: PlanStatus): Behavior[Command] = Behaviors.receiveMessagePartial {
        case Ingested(airing) =>
          val newStatus: PlanStatus = status.copy(
            added = status.added :+ airing
          )
          senderRef ! Added
          run(newStatus)
        case Disabled(airing) =>
          val newStatus: PlanStatus = status.copy(
            removed = status.removed :+ airing
          )
          senderRef ! Removed
          run(newStatus)
        case IngestFailure(airing, _) =>
          val newStatus: PlanStatus = status.copy(
            failures = status.failures :+ airing
          )
          senderRef ! Failed
          run(newStatus)
      }

      run(PlanStatus())
    }

    start()
  }

  private def getPlan(cache: Seq[GracenoteAiring], airings: Seq[GracenoteAiring]): AiringPlan = {
    // New airings that don't exist in the previous cache
    val add: Seq[GracenoteAiring] = airings.filter { airing: GracenoteAiring =>
      !cache.contains { cAiring: GracenoteAiring =>
        cAiring.program.rootId == airing.program.rootId &&
        cAiring.startTime == airing.startTime &&
        cAiring.endTime == airing.endTime
      }
    }

    // Airings that were cached but not in the latest pull
    val remove: Seq[GracenoteAiring] = cache.filter { cAiring: GracenoteAiring =>
      !airings.contains { airing: GracenoteAiring =>
        cAiring.program.rootId == airing.program.rootId &&
        cAiring.startTime == airing.startTime &&
        cAiring.endTime == airing.endTime
      }
    }

    // Airings that exist in both the cache and the new pull
    val skip: Seq[GracenoteAiring] =
      cache.diff(remove).intersect(airings)

    AiringPlan(
      add,
      remove,
      skip
    )
  }
}
