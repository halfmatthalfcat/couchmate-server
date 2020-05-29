package com.couchmate.external.gracenote.listing.program

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.SportEventDAO
import com.couchmate.data.db.services.DataServices
import com.couchmate.data.models.{SportEvent, SportOrganization}
import com.couchmate.external.gracenote.models.GracenoteAiring

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object SportEventIngester extends SportEventDAO {
  sealed trait Command

  final case class Ingested(event: SportEvent) extends Command
  final case class IngestFailure(err: Throwable) extends Command

  private final case class SportEventSuccess(sportEvent: Option[SportEvent]) extends Command
  private final case class SportEventFailure(err: Throwable) extends Command

  def apply(
    gnAiring: GracenoteAiring,
    sportOrg: SportOrganization,
    senderRef: ActorRef[Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val db: Database = DatabaseExtension(ctx.system).db

    ctx.pipeToSelf(getSportEventByNameAndOrg(
      gnAiring.program.eventTitle.getOrElse(
        gnAiring.program.title
      ),
      sportOrg.sportOrganizationId.get,
    )) {
      case Success(value) => SportEventSuccess(value)
      case Failure(exception) => SportEventFailure(exception)
    }

    def run(): Behavior[Command] = Behaviors.receiveMessage {
      case SportEventSuccess(Some(sportEvent)) =>
        senderRef ! Ingested(sportEvent)
        Behaviors.stopped

      case SportEventSuccess(None) =>
        ctx.pipeToSelf(upsertSportEvent(SportEvent(
          sportEventId = None,
          sportEventTitle = gnAiring.program.eventTitle.getOrElse(gnAiring.program.title),
          sportOrganizationId = sportOrg.sportOrganizationId.get,
        ))) {
          case Success(value) => SportEventSuccess(Some(value))
          case Failure(exception) => SportEventFailure(exception)
        }
        Behaviors.same

      case SportEventFailure(err) =>
        senderRef ! IngestFailure(err)
        Behaviors.stopped
    }

    run()
  }
}
