package com.couchmate.external.gracenote.listing.program

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.ShowDAO
import com.couchmate.data.models.Show
import com.couchmate.external.gracenote.models.GracenoteAiring
import com.couchmate.util.akka.extensions.DatabaseExtension

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ShowIngester extends ShowDAO {
  sealed trait Command

  final case class Ingested(show: Show) extends Command
  final case class IngestFailed(err: Throwable) extends Command

  private final case class ShowSuccess(show: Show) extends Command
  private final case class ShowFailure(err: Throwable) extends Command

  def apply(
    gnAiring: GracenoteAiring,
    senderRef: ActorRef[Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    ctx.pipeToSelf(upsertShow(Show(
      showId = None,
      extId = gnAiring.program.rootId,
      `type` = "show",
      episodeId = None,
      sportEventId = None,
      title = gnAiring.program.episodeTitle.getOrElse(gnAiring.program.title),
      description = gnAiring.program
        .shortDescription
        .orElse(gnAiring.program.longDescription)
        .getOrElse("N/A"),
      originalAirDate = gnAiring.program.origAirDate,
    ))) {
      case Success(value) => ShowSuccess(value)
      case Failure(exception) => ShowFailure(exception)
    }

    def run(): Behavior[Command] = Behaviors.receiveMessage {
      case ShowSuccess(show) =>
        senderRef ! Ingested(show)
        Behaviors.stopped
      case ShowFailure(err) =>
        senderRef ! IngestFailed(err)
        Behaviors.stopped
    }

    run()
  }
}
