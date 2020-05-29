package com.couchmate.external.gracenote.listing.program

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.ShowDAO
import com.couchmate.data.db.services.DataServices
import com.couchmate.data.models.{Show, SportEvent, SportOrganization}
import com.couchmate.external.gracenote.models.GracenoteAiring
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object SportIngester
  extends PlayJsonSupport
  with ShowDAO {
  sealed trait Command

  final case class Ingested(show: Show) extends Command
  final case class IngestFailed(err: Throwable) extends Command

  private final case class SportOrganizationSuccess(sportOrganization: SportOrganization) extends Command
  private final case class SportOrganizationFailure(err: Throwable) extends Command

  private final case class SportEventSuccess(event: SportEvent) extends Command
  private final case class SportEventFailure(err: Throwable) extends Command

  private final case class ShowSuccess(show: Show) extends Command
  private final case class ShowFailure(err: Throwable) extends Command

  def apply(
    gnAiring: GracenoteAiring,
    senderRef: ActorRef[Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    val sportOrgAdapter: ActorRef[SportOrganizationIngester.Command] = ctx.messageAdapter {
      case SportOrganizationIngester.Ingested(value) =>
        SportOrganizationSuccess(value)
      case SportOrganizationIngester.IngestFailure(err) =>
        SportOrganizationFailure(err)
    }
    val sportEventAdapter: ActorRef[SportEventIngester.Command] = ctx.messageAdapter {
      case SportEventIngester.Ingested(value) =>
        SportEventSuccess(value)
      case SportEventIngester.IngestFailure(err) =>
        SportEventFailure(err)
    }

    ctx.spawnAnonymous(SportOrganizationIngester(
      gnAiring,
      sportOrgAdapter
    ))

    def start(): Behavior[Command] = Behaviors.receiveMessage {
      case SportOrganizationSuccess(sportOrganization) =>
        ctx.spawnAnonymous(SportEventIngester(
          gnAiring,
          sportOrganization,
          sportEventAdapter
        ))
        withSportOrg()
      case SportOrganizationFailure(err) =>
        senderRef ! IngestFailed(err)
        Behaviors.stopped
    }

    def withSportOrg(): Behavior[Command] = Behaviors.receiveMessage {
      case SportEventSuccess(event) =>
        ctx.pipeToSelf(upsertShow(Show(
          showId = None,
          extId = gnAiring.program.rootId,
          `type` = "sport",
          episodeId = None,
          sportEventId = event.sportEventId,
          title = gnAiring.program.title,
          description = gnAiring.program
            .shortDescription
            .orElse(gnAiring.program.longDescription)
            .getOrElse("N/A"),
          originalAirDate = gnAiring.program.origAirDate
        ))) {
          case Success(value) => ShowSuccess(value)
          case Failure(exception) => ShowFailure(exception)
        }
        Behaviors.same
      case ShowSuccess(show) =>
        senderRef ! Ingested(show)
        Behaviors.stopped
      case ShowFailure(err) =>
        senderRef ! IngestFailed(err)
        Behaviors.stopped
      case SportEventFailure(err) =>
        senderRef ! IngestFailed(err)
        Behaviors.stopped
    }

    start()
  }

}
