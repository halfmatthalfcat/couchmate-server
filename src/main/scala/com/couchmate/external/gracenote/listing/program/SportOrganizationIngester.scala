package com.couchmate.external.gracenote.listing.program

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.ResponseEntity
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import com.couchmate.data.db.DatabaseExtension
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.SportOrganizationDAO
import com.couchmate.data.models.SportOrganization
import com.couchmate.external.gracenote.makeGracenoteRequest
import com.couchmate.external.gracenote.models.{GracenoteAiring, GracenoteSportOrganization, GracenoteSportResponse}
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object SportOrganizationIngester
  extends PlayJsonSupport
  with SportOrganizationDAO {
  sealed trait Command

  final case class Ingested(org: SportOrganization) extends Command
  final case class IngestFailure(err: Throwable) extends Command

  private final case class SportOrganizationSuccess(sportOrg: Option[SportOrganization]) extends Command
  private final case class SportOrganizationFailure(err: Throwable) extends Command

  private final case class RequestSuccess(entity: ResponseEntity) extends Command
  private final case class RequestFailure(err: Throwable) extends Command

  def apply(
    gnAiring: GracenoteAiring,
    senderRef: ActorRef[Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = Materializer(ctx)
    implicit val db: Database = DatabaseExtension(ctx.system).db
    val http: HttpExt = Http(ctx.system)
    val config: Config = ConfigFactory.load()
    val gnApiKey: String = config.getString("gracenote.apiKey")
    val gnHost: String = config.getString("gracenote.host")

    ctx.pipeToSelf(getSportOrganizationBySportAndOrg(
      gnAiring.program.sportsId.get,
      gnAiring.program.organizationId,
    )) {
      case Success(value) => SportOrganizationSuccess(value)
      case Failure(exception) => SportOrganizationFailure(exception)
    }

    def run(): Behavior[Command] = Behaviors.receiveMessage {
      case SportOrganizationSuccess(Some(sportOrganization)) =>
        senderRef ! Ingested(sportOrganization)
        Behaviors.stopped

      case SportOrganizationSuccess(None) =>
        ctx.pipeToSelf(http.singleRequest(makeGracenoteRequest(
          gnHost,
          gnApiKey,
          Seq("sports", gnAiring.program.sportsId.get.toString),
          Map(
            "includeOrg" -> Some("true"),
            "officialOrg" -> Some("true"),
          ),
        ))) {
          case Success(value) => RequestSuccess(value.entity)
          case Failure(exception) => RequestFailure(exception)
        }
        Behaviors.same

      case RequestSuccess(entity) =>
        ctx.pipeToSelf(Unmarshal(entity).to[Seq[GracenoteSportResponse]]) {
          case Success(value) =>
            val org: Option[GracenoteSportOrganization] =
              gnAiring.program.organizationId.flatMap(orgId => value.head.organizations.find(_.organizationId == orgId))
            SportOrganizationSuccess(Some(SportOrganization(
              sportOrganizationId = None,
              extSportId = gnAiring.program.sportsId.get,
              extOrgId = org.map(_.organizationId),
              sportName = value.head.sportsName,
              orgName = org.map(_.organizationName)
            )))
          case Failure(exception) => SportOrganizationFailure(exception)
        }
        Behaviors.same

      case RequestFailure(err) =>
        senderRef ! IngestFailure(err)
        Behaviors.stopped
      case SportOrganizationFailure(err) =>
        senderRef ! IngestFailure(err)
        Behaviors.stopped
    }

    run()
  }
}
