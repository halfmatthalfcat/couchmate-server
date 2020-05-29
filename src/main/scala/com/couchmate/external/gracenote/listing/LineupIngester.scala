package com.couchmate.external.gracenote.listing

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.{AiringDAO, LineupDAO}
import com.couchmate.data.db.services.DataServices
import com.couchmate.data.models.{Airing, Lineup, ProviderChannel, Show}
import com.couchmate.external.gracenote.listing.program.{EpisodeIngester, ShowIngester, SportIngester}
import com.couchmate.external.gracenote.models.GracenoteAiring

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object LineupIngester
  extends AiringDAO
  with LineupDAO {
  sealed trait Command

  final case class Ingested(gnAiring: GracenoteAiring) extends Command
  final case class IngestFailed(
    gracenoteAiring: GracenoteAiring,
    err: Throwable
  ) extends Command

  private final case class ShowSuccess(show: Show) extends Command
  private final case class ShowFailure(err: Throwable) extends Command

  private final case class AiringSuccess(airing: Option[Airing]) extends Command
  private final case class AiringFailure(err: Throwable) extends Command

  private final case class LineupSuccess(lineup: Option[Lineup]) extends Command
  private final case class LineupFailure(err: Throwable) extends Command

  def apply(
    gnAiring: GracenoteAiring,
    providerChannel: ProviderChannel,
    senderRef: ActorRef[Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    val episodeAdapter: ActorRef[EpisodeIngester.Command] = ctx.messageAdapter[EpisodeIngester.Command] {
      case EpisodeIngester.Ingested(value) => ShowSuccess(value)
      case EpisodeIngester.IngestFailed(err) => ShowFailure(err)
    }

    val sportAdapter: ActorRef[SportIngester.Command] = ctx.messageAdapter[SportIngester.Command] {
      case SportIngester.Ingested(show) => ShowSuccess(show)
      case SportIngester.IngestFailed(err) => ShowFailure(err)
    }

    val showAdapter: ActorRef[ShowIngester.Command] = ctx.messageAdapter[ShowIngester.Command] {
      case ShowIngester.Ingested(show) => ShowSuccess(show)
      case ShowIngester.IngestFailed(err) => ShowFailure(err)
    }

    gnAiring match {
      case GracenoteAiring(_, _, _, program) if program.seriesId.nonEmpty =>
        ctx.spawnAnonymous(EpisodeIngester(gnAiring, episodeAdapter))
      case GracenoteAiring(_, _, _, program) if program.sportsId.nonEmpty =>
        ctx.spawnAnonymous(SportIngester(gnAiring, sportAdapter))
      case _ =>
        ctx.spawnAnonymous(ShowIngester(gnAiring, showAdapter))
    }

    def run(): Behavior[Command] = Behaviors.receiveMessage {
      case ShowSuccess(show) =>
        ctx.pipeToSelf(getAiringByShowStartAndEnd(
          show.showId.get,
          gnAiring.startTime,
          gnAiring.endTime,
        )) {
          case Success(value) => AiringSuccess(value)
          case Failure(err) => AiringFailure(err)
        }
        withShow(show)
      case ShowFailure(err) =>
        senderRef ! IngestFailed(gnAiring, err)
        Behaviors.stopped
    }

    def withShow(show: Show): Behavior[Command] = Behaviors.receiveMessage {
      case AiringSuccess(Some(value)) => ctx.pipeToSelf(getLineupForProviderChannelAndAiring(
          providerChannel.providerChannelId.get,
          value.airingId.get,
        )) {
          case Success(value) => LineupSuccess(value)
          case Failure(err) => LineupFailure(err)
        }
        withAiring(value)
      case AiringSuccess(None) => ctx.pipeToSelf(upsertAiring(Airing(
        airingId = Some(UUID.randomUUID()),
        showId = show.showId.get,
        startTime = gnAiring.startTime,
        endTime = gnAiring.endTime,
        duration = gnAiring.duration
      ))) {
        case Success(value) => AiringSuccess(Some(value))
        case Failure(err) => AiringFailure(err)
      }
        Behaviors.same
      case AiringFailure(err) =>
        senderRef ! IngestFailed(gnAiring, err)
        Behaviors.stopped
    }

    def withAiring(airing: Airing): Behavior[Command] = Behaviors.receiveMessage {
      case LineupSuccess(_) =>
        senderRef ! Ingested(gnAiring)
        Behaviors.stopped
      case LineupSuccess(None) => ctx.pipeToSelf(upsertLineup(Lineup(
        lineupId = None,
        providerChannelId = providerChannel.providerChannelId.get,
        airingId = airing.airingId.get,
        active = true
      ))) {
        case Success(value) => LineupSuccess(Some(value))
        case Failure(exception) => LineupFailure(exception)
      }
        Behaviors.same
      case LineupFailure(err) =>
        senderRef ! IngestFailed(gnAiring, err)
        Behaviors.stopped
    }

    run()
  }
}
