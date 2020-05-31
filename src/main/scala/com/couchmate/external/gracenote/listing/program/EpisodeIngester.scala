package com.couchmate.external.gracenote.listing.program

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.{EpisodeDAO, SeriesDAO, ShowDAO}
import com.couchmate.data.models.{Episode, Series, Show}
import com.couchmate.external.gracenote.models.GracenoteAiring
import com.couchmate.util.akka.extensions.DatabaseExtension

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object EpisodeIngester
  extends EpisodeDAO
  with SeriesDAO
  with ShowDAO {
  sealed trait Command

  final case class Ingested(show: Show) extends Command
  final case class IngestFailed(err: Throwable) extends Command

  private final case class SeriesSuccess(series: Option[Series]) extends Command
  private final case class SeriesFailure(err: Throwable) extends Command

  private final case class EpisodeSuccess(episode: Episode) extends Command
  private final case class EpisodeFailure(err: Throwable) extends Command

  private final case class ShowSuccess(show: Show) extends Command
  private final case class ShowFailure(err: Throwable) extends Command

  def apply(
    gnAiring: GracenoteAiring,
    senderRef: ActorRef[Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    ctx.pipeToSelf(getSeriesByExt(
      gnAiring.program.seriesId.get
    )) {
      case Success(value) => SeriesSuccess(value)
      case Failure(exception) => SeriesFailure(exception)
    }

    def start(): Behavior[Command] = Behaviors.receiveMessage {
      case SeriesSuccess(Some(series)) =>
        withSeries(series)
      case SeriesSuccess(None) =>
        ctx.pipeToSelf(upsertSeries(Series(
          seriesId = None,
          extId = gnAiring.program.seriesId.get,
          seriesName = gnAiring.program.title,
          totalSeasons = None,
          totalEpisodes = None
        ))) {
          case Success(value) => SeriesSuccess(Some(value))
          case Failure(exception) => SeriesFailure(exception)
        }
        Behaviors.same
      case SeriesFailure(err) =>
        senderRef ! IngestFailed(err)
        Behaviors.stopped
    }

    def withSeries(series: Series): Behavior[Command] = {
      ctx.pipeToSelf(upsertEpisode(Episode(
        episodeId = None,
        seriesId = series.seriesId.get,
        season = gnAiring.program.seasonNumber,
        episode = gnAiring.program.episodeNumber
      ))) {
        case Success(value) => EpisodeSuccess(value)
        case Failure(exception) => EpisodeFailure(exception)
      }

      Behaviors.receiveMessage {
        case EpisodeSuccess(value) =>
          withEpisode(value)
        case EpisodeFailure(err) =>
          senderRef ! IngestFailed(err)
          Behaviors.stopped
      }
    }

    def withEpisode(episode: Episode): Behavior[Command] = {
      ctx.pipeToSelf(upsertShow(Show(
        showId = None,
        extId = gnAiring.program.rootId,
        `type` = "episode",
        episodeId = episode.episodeId,
        sportEventId = None,
        title = gnAiring.program.episodeTitle.getOrElse(
          gnAiring.program.title
        ),
        description = gnAiring.program
                              .shortDescription
                              .orElse(gnAiring.program.longDescription)
                              .getOrElse("N/A"),
        originalAirDate = gnAiring.program.origAirDate,
      ))) {
        case Success(value) => ShowSuccess(value)
        case Failure(exception) => ShowFailure(exception)
      }

      Behaviors.receiveMessage {
        case ShowSuccess(show) =>
          senderRef ! Ingested(show)
          Behaviors.stopped
        case ShowFailure(err) =>
          senderRef ! IngestFailed(err)
          Behaviors.stopped
      }
    }

    start()
  }
}
